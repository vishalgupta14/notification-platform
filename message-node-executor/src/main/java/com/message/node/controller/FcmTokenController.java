package com.message.node.controller;

import com.message.node.service.FcmTokenService;
import com.notification.common.model.FcmTokenEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @PostMapping("/register")
    public Mono<String> registerToken(@RequestBody FcmTokenEntity token) {
        return fcmTokenService.registerToken(token)
                .thenReturn("✅ FCM token registered successfully");
    }
}
