package com.message.node.rate.limiter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    @Value("${ratelimiter.enabled:true}")
    private boolean isRateLimiterEnabled;

    @Value("${ratelimiter.email.limit:60}")
    private int emailLimit;

    @Value("${ratelimiter.sms.limit:30}")
    private int smsLimit;

    @Value("${ratelimiter.whatsapp.limit:40}")
    private int whatsappLimit;

    @Value("${ratelimiter.push.limit:50}")
    private int pushLimit;

    @Value("${ratelimiter.voice.limit:20}")
    private int voiceLimit;

    @Value("${ratelimiter.webhook.limit:25}")
    private int webhookLimit;

    @Value("${ratelimiter.publish.limit:100}")
    private int publishLimit;

    @Value("${email.queue.name:email-queue}")
    private String emailQueue;

    @Value("${sms.queue.name:sms-queue}")
    private String smsQueue;

    @Value("${whatsapp.queue.name:whatsapp-queue}")
    private String whatsappQueue;

    @Value("${push.queue.name:push-notification-queue}")
    private String pushQueue;

    @Value("${voice.queue.name:voice-notification-queue}")
    private String voiceQueue;

    @Value("${webhook.queue.name:webhook-queue}")
    private String webhookQueue;

    @Value("${publish.queue.name:publish-queue}")
    private String publishQueue;

    private final Map<String, Integer> queueLimitMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initLimits() {
        queueLimitMap.put(emailQueue, emailLimit);
        queueLimitMap.put(smsQueue, smsLimit);
        queueLimitMap.put(whatsappQueue, whatsappLimit);
        queueLimitMap.put(pushQueue, pushLimit);
        queueLimitMap.put(voiceQueue, voiceLimit);
        queueLimitMap.put(webhookQueue, webhookLimit);
        queueLimitMap.put(publishQueue, publishLimit);
    }

    public boolean isAllowed(String queueName) {
        if (!isRateLimiterEnabled) return true;

        var limiter = limiters.computeIfAbsent(queueName, this::createRateLimiter);
        return limiter.acquirePermission();
    }

    private RateLimiter createRateLimiter(String queueName) {
        int limit = queueLimitMap.getOrDefault(queueName, 60);

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limit)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMillis(0))
                .build();

        return RateLimiterRegistry.of(config).rateLimiter(queueName);
    }
}
