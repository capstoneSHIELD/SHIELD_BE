package org.example.shield.fcm.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.shield.fcm.domain.DeviceType;
import org.example.shield.fcm.domain.FcmTokenWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmTokenService {

    private final FcmTokenWriter fcmTokenWriter;

    @Transactional
    public void register(UUID userId, String token, DeviceType deviceType) {
        fcmTokenWriter.upsertToken(userId, token, deviceType);
        log.info("FCM 토큰 UPSERT 완료. userId={}, deviceType={}", userId, deviceType);
    }

    @Transactional
    public void unregister(String token) {
        fcmTokenWriter.deleteByToken(token);
        log.info("FCM 토큰 해제. token={}", token);
    }
}
