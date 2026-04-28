package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    /** Tìm tất cả device đã đăng ký của user — push gửi parallel tới mọi device. */
    List<DeviceToken> findByUserDid(String userDid);

    /** Cleanup khi mobile uninstall + revoke FCM token (báo lỗi 404 từ FCM/APNs). */
    long deleteByFcmToken(String fcmToken);

    long deleteByApnsToken(String apnsToken);
}
