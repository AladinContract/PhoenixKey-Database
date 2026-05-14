package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "magic_claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MagicClaim {

    @Id
    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "user_did", nullable = false, length = 120)
    private String userDid;

    @Column(name = "amount_magic", nullable = false)
    private Long amountMagic;

    @Column(name = "claimed_slot", nullable = false)
    private Long claimedSlot;

    @Column(name = "cardano_tx_hash", length = 64, unique = true)
    private String cardanoTxHash;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "fail_reason", length = 300)
    private String failReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    public enum Status { PENDING, SUBMITTED, CONFIRMED, FAILED }
}
