package com.message.engine.service.notification;

import com.notification.common.model.FcmTokenEntity;
import com.notification.common.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository tokenRepository;

    public String resolveFcmToken(String phone) {
       if (StringUtils.isNotBlank(phone)) {
            return tokenRepository.findByPhone(phone)
                    .map(FcmTokenEntity::getFcmToken)
                    .orElse(null);
        }
        return null;
    }

    public FcmTokenEntity registerToken(FcmTokenEntity tokenEntity) {
        return tokenRepository.save(tokenEntity);
    }

    public Optional<FcmTokenEntity> findByPhone(String phone) {
        return tokenRepository.findByPhone(phone);
    }
}
