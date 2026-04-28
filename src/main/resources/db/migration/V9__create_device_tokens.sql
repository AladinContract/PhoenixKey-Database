-- ============================================================
-- V9: Device tokens cho push notification (Phase D.4)
--
-- Mobile gọi POST /devices/register sau khi login để lưu FCM/APNs token.
-- Server lookup theo user_did, gửi push qua FcmPushSender / ApnsPushSender.
--
-- Ưu tiên APNs (iOS) trên FCM (cross-platform) khi user có cả 2 — stable hơn.
-- ============================================================

CREATE TABLE device_tokens (
    id            UUID PRIMARY KEY,
    user_did      VARCHAR(128) NOT NULL REFERENCES users(user_did) ON DELETE CASCADE,
    platform      VARCHAR(10)  NOT NULL,
    fcm_token     VARCHAR(255),
    apns_token    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at  TIMESTAMPTZ,
    CONSTRAINT chk_device_platform CHECK (platform IN ('ios', 'android')),
    CONSTRAINT chk_device_token_present CHECK (fcm_token IS NOT NULL OR apns_token IS NOT NULL)
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_did);

COMMENT ON COLUMN device_tokens.platform   IS 'ios | android';
COMMENT ON COLUMN device_tokens.fcm_token  IS 'Firebase Cloud Messaging token (Android + iOS qua FCM gateway)';
COMMENT ON COLUMN device_tokens.apns_token IS 'APNs device token (iOS native, ưu tiên hơn FCM)';
