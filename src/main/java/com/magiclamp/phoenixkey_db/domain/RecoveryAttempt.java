package com.magiclamp.phoenixkey_db.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recovery_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecoveryAttempt {

    @Id
    @Column(name = "recovery_id")
    private UUID recoveryId;

    @Column(name = "user_did", nullable = false, length = 120)
    private String userDid;

    @Column(name = "initiator_type", nullable = false, length = 20)
    private String initiatorType;

    @Column(name = "pending_pubkey_hex", length = 200)
    private String pendingPubkeyHex;

    @Column(name = "pending_controller_pkh", length = 60)
    private String pendingControllerPkh;

    @Column(name = "guardian_signatures", columnDefinition = "jsonb")
    private String guardianSignaturesJson;

    @Column(name = "collateral_address", length = 150)
    private String collateralAddress;

    @Column(name = "collateral_lovelace")
    private Long collateralLovelace;

    @Column(name = "deadline_slot")
    private Long deadlineSlot;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RecoveryStatus status;

    @Column(name = "init_tx_hash", length = 64)
    private String initTxHash;

    @Column(name = "cancel_tx_hash", length = 64)
    private String cancelTxHash;

    @Column(name = "finalize_tx_hash", length = 64)
    private String finalizeTxHash;

    @Column(name = "fail_reason", length = 300)
    private String failReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;
}
