package com.message.scheduler;

import com.message.scheduler.producer.MessageProducer;
import com.notification.common.dto.NotificationPayloadDTO;
import com.notification.common.model.ScheduledNotification;
import com.notification.common.repository.ScheduledNotificationRepository;
import com.notification.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.support.CronExpression;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledNotificationService {

    private final ScheduledNotificationRepository scheduledRepo;
    private final MessageProducer messageProducer;

    @Scheduled(fixedRateString = "${notification.scheduler.fixedRate.ms}")
    public void processScheduledNotifications() {
        LocalDateTime now = LocalDateTime.now();
        log.info("⏰ Scheduler triggered at: {}", now);

        List<ScheduledNotification> dueNotifications = scheduledRepo.findByActiveTrue();
        log.info("🔍 Found {} active scheduled notifications to evaluate", dueNotifications.size());

        for (ScheduledNotification scheduled : dueNotifications) {
            try {
                log.debug("🔎 Checking scheduled ID: {}, Cron: {}", scheduled.getId(), scheduled.getScheduleCron());

                if (isCronDueNow(scheduled)) {
                    log.info("✅ Matched cron for scheduled ID: {}, Queue: {}", scheduled.getId(), scheduled.getQueueName());
                    publishToQueue(scheduled);

                    if (isOneTimeJob(scheduled)) {
                        scheduledRepo.deleteById(scheduled.getId());
                        log.info("🗑️ One-time scheduled notification deleted: {}", scheduled.getId());
                    }
                } else {
                    log.debug("⏭️ Not due yet for scheduled ID: {}", scheduled.getId());
                }

            } catch (Exception e) {
                log.error("⚠️ Error processing scheduled notification ID {}: {}", scheduled.getId(), e.getMessage(), e);
            }
        }

        log.info("✅ Scheduler cycle completed at: {}", LocalDateTime.now());
    }

    private boolean isOneTimeJob(ScheduledNotification scheduled) {
        try {
            CronExpression cron = CronExpression.parse(scheduled.getScheduleCron());
            String zoneIdStr = Optional.ofNullable(scheduled.getTimeZone()).orElse(ZoneId.systemDefault().getId());
            ZoneId zoneId = ZoneId.of(zoneIdStr);

            ZonedDateTime now = ZonedDateTime.now(zoneId);
            ZonedDateTime next = cron.next(now);
            if (next == null) {
                return true; // No future runs → one-time cron
            }

            // Optional: Add tolerance if it's near expiration
            ZonedDateTime nextAfter = cron.next(next);
            return nextAfter == null;

        } catch (Exception e) {
            log.warn("⚠️ Failed to evaluate if cron is one-time. Treating as recurring. {}", e.getMessage());
            return false;
        }
    }

    private void publishToQueue(ScheduledNotification scheduled) {
        String queueName = scheduled.getQueueName();


        NotificationPayloadDTO payload = new NotificationPayloadDTO();
        payload.setTo(scheduled.getTo());
        payload.setCc(scheduled.getCc());
        payload.setBcc(scheduled.getBcc());
        payload.setSubject(scheduled.getEmailSubject());
        payload.setSnapshotConfig(scheduled.getNotificationConfig());
        payload.setSnapshotTemplate(scheduled.getTemplate());

        String jsonPayload = JsonUtil.toJsonWithJavaTime(payload);
        messageProducer.sendMessage(queueName, jsonPayload, false);

        log.info("📤 Scheduled message sent to queue: {}", queueName);
    }

    private boolean isCronDueNow(ScheduledNotification scheduled) {
        try {
            CronExpression cron = CronExpression.parse(scheduled.getScheduleCron());

            String zoneIdStr = Optional.ofNullable(scheduled.getTimeZone()).orElse(ZoneId.systemDefault().getId());
            ZoneId zoneId = ZoneId.of(zoneIdStr);

            ZonedDateTime now = LocalDateTime.now().atZone(zoneId);
            ZonedDateTime start = now.minusSeconds(30);
            ZonedDateTime end = now.plusSeconds(30);

            ZonedDateTime next = cron.next(start);
            return next != null && !next.isAfter(end);

        } catch (Exception e) {
            log.error("❌ Error parsing cron or zone for scheduled notification: {}", e.getMessage(), e);
            return false;
        }
    }

}
