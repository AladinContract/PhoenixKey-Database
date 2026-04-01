phoenixkey-service/
├── pom.xml
├── src/
│ ├── main/
│ │ ├── java/com/magiclamp/phoenixkey/
│ │ │ ├── PhoenixKeyApplication.java
│ │ │ │
│ │ │ ├── config/
│ │ │ │ ├── VaultConfig.java # Đọc SERVER_PEPPER từ Vault
│ │ │ │ ├── RedisConfig.java
│ │ │ │ └── SecurityConfig.java
│ │ │ │
│ │ │ ├── domain/ # JPA Entities (ánh xạ 1-1 với schema)
│ │ │ │ ├── User.java
│ │ │ │ ├── AuthMethod.java
│ │ │ │ ├── AuthProvider.java # Enum: google, apple, phone
│ │ │ │ ├── AuthorizedKey.java
│ │ │ │ ├── Guardian.java
│ │ │ │ ├── OnchainTaadStateCache.java
│ │ │ │ ├── ActivityLog.java
│ │ │ │ └── TaadStatus.java # Enum: ACTIVE, RECOVERING, MIGRATED
│ │ │ │
│ │ │ ├── repository/ # Spring Data JPA repositories
│ │ │ │ ├── UserRepository.java
│ │ │ │ ├── AuthMethodRepository.java
│ │ │ │ ├── AuthorizedKeyRepository.java
│ │ │ │ ├── GuardianRepository.java
│ │ │ │ ├── OnchainTaadStateCacheRepository.java
│ │ │ │ └── ActivityLogRepository.java
│ │ │ │
│ │ │ └── crypto/
│ │ │ └── BlindIndexService.java # HMAC-SHA256 + Pepper logic
│ │ │
│ │ └── resources/
│ │ ├── application.yml
│ │ ├── application-prod.yml
│ │ └── db/migration/ # Flyway migrations
│ │ ├── V1**create_identity_core.sql
│ │ ├── V2**create_authorized_keys.sql
│ │ ├── V3**create_guardians.sql
│ │ ├── V4**create_onchain_cache.sql
│ │ └── V5\_\_create_activity_logs.sql
│ │
│ └── test/
│ └── java/com/magiclamp/phoenixkey/
│ ├── crypto/BlindIndexServiceTest.java
│ └── repository/UserRepositoryTest.java
