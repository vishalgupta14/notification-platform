package com.message.node.controller;

import com.message.node.service.FcmTokenService;
import com.notification.common.model.FcmTokenEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @PostMapping("/register")
    public ResponseEntity<String> registerToken(@RequestBody FcmTokenEntity token) {
        fcmTokenService.registerToken(token);
        return ResponseEntity.ok("✅ FCM token registered successfully");
    }
}
