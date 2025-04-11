package com.message.scheduler;

import com.message.scheduler.producer.MessageProducer;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.ScheduledNotification;
import com.notification.common.repository.ScheduledNotificationRepository;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledNotificationService {

    private final ScheduledNotificationRepository scheduledRepo;
    private final ReactiveMongoTemplate mongoTemplate;
    private final MessageProducer messageProducer;

    private static final String INSTANCE_ID = System.getenv().getOrDefault("INSTANCE_ID", "scheduler-instance-1");
    private static final int MAX_RETRIES = 3;

    @Scheduled(fixedRateString = "${notification.scheduler.fixedRate.ms}")
    public void processScheduledNotifications() {
        log.info("⏰ Scheduler triggered at: {}", LocalDateTime.now());

        Mono.defer(this::findAndLockNextJob)
                .repeat(5)
                .flatMap(this::processJobSafely)
                .doOnError(e -> log.error("❌ Unexpected error during scheduling run: {}", e.getMessage(), e))
                .doOnComplete(() -> log.info("✅ Scheduler run completed at: {}", LocalDateTime.now()))
                .subscribe();
    }

    private Mono<ScheduledNotification> findAndLockNextJob() {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("status").is("NEW"),
                Criteria.where("status").is("PICKED")
                        .and("pickedAt").lt(LocalDateTime.now().minusMinutes(2)) // stale lock
        ).and("active").is(true));

        Update update = new Update()
                .set("status", "PICKED")
                .set("pickedAt", LocalDateTime.now())
                .set("pickedBy", INSTANCE_ID);

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);

        return mongoTemplate.findAndModify(query, update, options, ScheduledNotification.class)
                .filter(scheduled -> scheduled != null);
    }

    private Mono<Boolean> processJobSafely(ScheduledNotification scheduled) {
        log.debug("🔎 Processing scheduled ID: {} with cron: {}", scheduled.getId(), scheduled.getScheduleCron());

        if (!isCronDueNow(scheduled)) {
            return unlockJob(scheduled);
        }

        try {
            publishToQueue(scheduled);
        } catch (Exception e) {
            log.error("❌ Failed to publish to queue for ID {}: {}", scheduled.getId(), e.getMessage(), e);
            return handleFailure(scheduled);
        }

        if (isOneTimeJob(scheduled)) {
            return scheduledRepo.deleteById(scheduled.getId())
                    .doOnSuccess(v -> log.info("🗑️ One-time job deleted: {}", scheduled.getId()))
                    .thenReturn(true);
        } else {
            scheduled.setStatus("COMPLETED");
            return scheduledRepo.save(scheduled).thenReturn(true);
        }
    }

    private Mono<Boolean> unlockJob(ScheduledNotification scheduled) {
        scheduled.setStatus("NEW");
        scheduled.setPickedBy(null);
        scheduled.setPickedAt(null);
        return scheduledRepo.save(scheduled).thenReturn(false);
    }

    private Mono<Boolean> handleFailure(ScheduledNotification scheduled) {
        int retry = scheduled.getRetryCount() + 1;
        scheduled.setRetryCount(retry);
        if (retry >= MAX_RETRIES) {
            scheduled.setStatus("FAILED");
        } else {
            scheduled.setStatus("NEW");
        }
        return scheduledRepo.save(scheduled).thenReturn(false);
    }

    private boolean isCronDueNow(ScheduledNotification scheduled) {
        try {
            CronExpression cron = CronExpression.parse(scheduled.getScheduleCron());
            ZoneId zoneId = ZoneId.of(Optional.ofNullable(scheduled.getTimeZone()).orElse(ZoneId.systemDefault().getId()));
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            ZonedDateTime windowStart = now.minusSeconds(30);
            ZonedDateTime windowEnd = now.plusSeconds(30);

            ZonedDateTime next = cron.next(windowStart);
            return next != null && !next.isAfter(windowEnd);
        } catch (Exception e) {
            log.error("❌ Cron parsing error for scheduled ID {}: {}", scheduled.getId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean isOneTimeJob(ScheduledNotification scheduled) {
        try {
            CronExpression cron = CronExpression.parse(scheduled.getScheduleCron());
            ZoneId zoneId = ZoneId.of(Optional.ofNullable(scheduled.getTimeZone()).orElse(ZoneId.systemDefault().getId()));
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            ZonedDateTime next = cron.next(now);
            if (next == null) return true;

            ZonedDateTime afterNext = cron.next(next);
            return afterNext == null;
        } catch (Exception e) {
            log.warn("⚠️ Could not determine if job is one-time for ID {}. {}", scheduled.getId(), e.getMessage());
            return false;
        }
    }

    private void publishToQueue(ScheduledNotification scheduled) {
        NotificationPayloadDTO payload = new NotificationPayloadDTO();
        payload.setTo(scheduled.getTo());
        payload.setCc(scheduled.getCc());
        payload.setBcc(scheduled.getBcc());
        payload.setSubject(scheduled.getEmailSubject());
        payload.setSnapshotConfig(scheduled.getNotificationConfig());
        payload.setSnapshotTemplate(scheduled.getTemplate());

        String queue = scheduled.getQueueName();
        String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);

        messageProducer.sendMessage(queue, jsonPayload, false);
        log.info("📤 Published message to queue [{}] for scheduled ID: {}", queue, scheduled.getId());
    }
}
