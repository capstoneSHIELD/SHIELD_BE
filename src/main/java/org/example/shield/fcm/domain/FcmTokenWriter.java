package org.example.shield.fcm.domain;

import java.util.UUID;

public interface FcmTokenWriter {

    FcmToken save(FcmToken token);

    void deleteByToken(String token);

    void upsertToken(UUID userId, String token, DeviceType deviceType);
}
