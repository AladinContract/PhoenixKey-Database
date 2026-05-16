package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "genies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Genie {

    @Id
    @Column(name = "genie_did", length = 120)
    private String genieDid;

    @Column(name = "wallet_address", nullable = false, length = 150)
    private String walletAddress;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GenieStatus status;

    @Column(name = "min_balance_lamp", nullable = false)
    private Long minBalanceLamp;

    @Column(name = "min_balance_lovelace", nullable = false)
    private Long minBalanceLovelace;

    @Column(name = "current_activations", nullable = false)
    private Integer currentActivations;

    @Column(name = "max_concurrent", nullable = false)
    private Integer maxConcurrent;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    public enum GenieStatus {
        AVAILABLE,  // Có đủ balance + slot trống
        BUSY,       // Đạt max_concurrent
        OFFLINE     // Last_seen > 5 phút trước
    }
}
