package com.message.node.service;

import com.notification.common.model.FcmTokenEntity;
import com.notification.common.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository tokenRepository;

    public Mono<String> resolveFcmToken(String phone) {
        if (StringUtils.isNotBlank(phone)) {
            return tokenRepository.findByPhone(phone)
                    .map(FcmTokenEntity::getFcmToken);
        }
        return Mono.empty();
    }

    public Mono<FcmTokenEntity> registerToken(FcmTokenEntity tokenEntity) {
        return tokenRepository.save(tokenEntity);
    }

    public Mono<FcmTokenEntity> findByPhone(String phone) {
        return tokenRepository.findByPhone(phone);
    }
}
