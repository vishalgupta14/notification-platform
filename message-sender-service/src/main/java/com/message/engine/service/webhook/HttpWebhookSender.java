package com.message.engine.service.webhook;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component("http")
public class HttpWebhookSender implements WebhookSender {

    private final WebClient webClient = WebClient.builder()
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();

    @Override
    public Mono<Void> sendWebhook(Map<String, Object> config, String to, String messageBody) {
        return webClient.post()
                .uri(to)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(messageBody)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Webhook failed: " + error))))
                .toBodilessEntity()
                .then(); // Convert to Mono<Void>
    }
}
