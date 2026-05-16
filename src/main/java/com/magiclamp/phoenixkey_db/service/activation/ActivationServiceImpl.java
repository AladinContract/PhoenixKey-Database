package com.magiclamp.phoenixkey_db.service.activation;

import com.magiclamp.phoenixkey_db.domain.*;
import com.magiclamp.phoenixkey_db.dto.activation.ActivationDtos.*;
import com.magiclamp.phoenixkey_db.dto.activation.ActivationEvent;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.ActivationRepository;
import com.magiclamp.phoenixkey_db.repository.GenieRepository;
import com.magiclamp.phoenixkey_db.service.activity.ActivityLogService;
import com.magiclamp.phoenixkey_db.service.cardano.BlockfrostHttpClient;
import com.magiclamp.phoenixkey_db.service.proofchat.ProofChatService;
import com.magiclamp.phoenixkey_db.service.push.PushService;
import com.magiclamp.phoenixkey_db.service.sse.ActivationEventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivationServiceImpl implements ActivationService {

    private final ActivationRepository activationRepo;
    private final GenieRepository genieRepo;
    private final ProofChatService proofChatService;
    private final PushService pushService;
    private final BlockfrostHttpClient blockfrost;
    private final ActivityLogService activityLogService;
    private final ActivationEventBus eventBus;

    @Value("${phoenixkey.activation.amount-vnd:200000}")
    private int amountVnd;

    @Value("${phoenixkey.activation.amount-lamp:1001}")
    private long amountLamp;

    @Value("${phoenixkey.activation.amount-lovelace:10000000}")  // 10 ADA
    private long amountLovelace;

    @Value("${phoenixkey.activation.ttl-minutes:15}")
    private int ttlMinutes;

    @Value("${phoenixkey.activation.payment-qr-base:https://pay.aladin.work/qr}")
    private String paymentQrBase;

    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ActivationInitiateResponse initiate(String userDid, String walletAddress) {
        // 1. Claim a Genie (atomic, SKIP LOCKED)
        Genie genie = genieRepo.claimAvailable()
                .orElseThrow(() -> new AppException(ErrorCode.NO_GENIE_AVAILABLE,
                        "Hiện không có Genie nào sẵn sàng. Vui lòng thử lại sau."));
        genieRepo.incrementLoad(genie.getGenieDid());

        // 2. Create activation row
        UUID activationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plus(Duration.ofMinutes(ttlMinutes));

        String paymentRef = generatePaymentReference(activationId);
        String qrUrl = paymentQrBase + "?ref=" + paymentRef + "&amount=" + amountVnd;

        // 3. Open ProofChat session
        String proofChatSessionId;
        String proofChatUrl;
        try {
            var pc = proofChatService.openSession(userDid, genie.getGenieDid(),
                    walletAddress, "ACTIVATION_PACKAGE", activationId.toString());
            proofChatSessionId = pc.sessionId();
            proofChatUrl = pc.embedUrl();
        } catch (Exception e) {
            log.error("ProofChat session open failed, rolling back genie claim: {}", e.getMessage());
            genieRepo.decrementLoad(genie.getGenieDid());
            throw new AppException(ErrorCode.PROOFCHAT_UNAVAILABLE,
                    "Dịch vụ chat hỗ trợ tạm bảo trì. Vui lòng thử lại sau.");
        }

        Activation activation = Activation.builder()
                .activationId(activationId)
                .userDid(userDid)
                .walletAddress(walletAddress)
                .genieDid(genie.getGenieDid())
                .status(ActivationStatus.PENDING_PAYMENT)
                .amountVnd(amountVnd)
                .amountLamp(amountLamp)
                .amountLovelace(amountLovelace)
                .paymentQrUrl(qrUrl)
                .paymentReference(paymentRef)
                .proofchatSessionId(proofChatSessionId)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
        activationRepo.save(activation);

        activityLogService.log(userDid, "activation_initiated",
                "amount=" + amountVnd + "đ, genie=" + truncate(genie.getGenieDid()));

        // 4. Notify Genie's app
        pushService.notifyActivationRequest(genie.getGenieDid(), activationId.toString());

        log.info("Activation {} initiated: user={}, genie={}, amount={}đ",
                activationId, truncate(userDid), truncate(genie.getGenieDid()), amountVnd);

        return new ActivationInitiateResponse(
                activationId.toString(),
                qrUrl,
                amountVnd,
                amountLamp,
                amountLovelace,
                genie.getGenieDid(),
                proofChatUrl,
                expiresAt.toEpochSecond()
        );
    }

    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void confirmPayment(UUID activationId, String paymentReference) {
        Activation activation = activationRepo.findById(activationId)
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVATION_NOT_FOUND));

        if (activation.getStatus() != ActivationStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.ACTIVATION_INVALID_STATE,
                    "Activation đang ở trạng thái " + activation.getStatus() + ", không thể confirm payment");
        }

        if (activation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            activation.setStatus(ActivationStatus.EXPIRED);
            activationRepo.save(activation);
            genieRepo.decrementLoad(activation.getGenieDid());
            throw new AppException(ErrorCode.ACTIVATION_EXPIRED);
        }

        if (paymentReference != null && !paymentReference.equals(activation.getPaymentReference())) {
            log.warn("Payment reference mismatch: expected={}, got={}",
                    activation.getPaymentReference(), paymentReference);
            throw new AppException(ErrorCode.ACTIVATION_INVALID_STATE, "Payment reference mismatch");
        }

        activation.setStatus(ActivationStatus.PAYMENT_CONFIRMED);
        activation.setPaidAt(OffsetDateTime.now());
        activationRepo.save(activation);

        activityLogService.log(activation.getUserDid(), "activation_paid",
                "ref=" + paymentReference);

        // Emit SSE event tới web user
        eventBus.publish(activationId.toString(),
                new ActivationEvent("payment_confirmed",
                        new ActivationEvent.Payload(activation.getStatus().name(), null, null)));

        // Push tới Genie's app
        pushService.notifyActivationPaymentReceived(activation.getGenieDid(), activationId.toString());

        log.info("Activation {} payment confirmed", activationId);
    }

    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ActivationSubmitTxResponse submitTx(UUID activationId, String genieDid, String signedTxCbor) {
        Activation activation = activationRepo.findById(activationId)
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVATION_NOT_FOUND));

        if (!genieDid.equals(activation.getGenieDid())) {
            throw new AppException(ErrorCode.ACTIVATION_NOT_AUTHORIZED,
                    "Activation này được giao cho Genie khác");
        }

        if (activation.getStatus() != ActivationStatus.PAYMENT_CONFIRMED) {
            throw new AppException(ErrorCode.ACTIVATION_INVALID_STATE,
                    "Activation chưa được PAYMENT_CONFIRMED");
        }

        // Submit signed CBOR to Cardano via Blockfrost
        String txHash;
        try {
            txHash = blockfrost.submitSignedTx(signedTxCbor);
        } catch (Exception e) {
            log.error("Activation {} tx submit failed: {}", activationId, e.getMessage());
            activation.setStatus(ActivationStatus.FAILED);
            activation.setFailReason("Cardano submit failed: " + truncate(e.getMessage(), 280));
            activationRepo.save(activation);
            genieRepo.decrementLoad(activation.getGenieDid());
            eventBus.publish(activationId.toString(),
                    new ActivationEvent("failed",
                            new ActivationEvent.Payload("FAILED", null, e.getMessage())));
            throw new AppException(ErrorCode.CARDANO_TX_FAILED);
        }

        activation.setCardanoTxHash(txHash);
        activation.setStatus(ActivationStatus.ACTIVATED);
        activation.setActivatedAt(OffsetDateTime.now());
        activationRepo.save(activation);

        genieRepo.decrementLoad(activation.getGenieDid());

        activityLogService.log(activation.getUserDid(), "activation_completed",
                "tx=" + txHash);
        activityLogService.log(activation.getGenieDid(), "genie_fulfilled_activation",
                "user=" + truncate(activation.getUserDid()) + ", tx=" + txHash);

        eventBus.publish(activationId.toString(),
                new ActivationEvent("activated",
                        new ActivationEvent.Payload("ACTIVATED", txHash, null)));

        log.info("Activation {} completed: tx={}", activationId, txHash);

        return new ActivationSubmitTxResponse(txHash, ActivationStatus.ACTIVATED.name());
    }

    // ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void cancel(UUID activationId, String userDid) {
        Activation activation = activationRepo.findById(activationId)
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVATION_NOT_FOUND));

        if (!userDid.equals(activation.getUserDid())) {
            throw new AppException(ErrorCode.ACTIVATION_NOT_AUTHORIZED);
        }

        if (activation.getStatus() != ActivationStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.ACTIVATION_INVALID_STATE,
                    "Chỉ huỷ được khi đang PENDING_PAYMENT");
        }

        activation.setStatus(ActivationStatus.CANCELLED);
        activationRepo.save(activation);

        genieRepo.decrementLoad(activation.getGenieDid());

        activityLogService.log(userDid, "activation_cancelled", "");

        eventBus.publish(activationId.toString(),
                new ActivationEvent("cancelled",
                        new ActivationEvent.Payload("CANCELLED", null, null)));

        log.info("Activation {} cancelled by user", activationId);
    }

    // ─────────────────────────────────────────────────────────────

    @Override
    public ActivationStatusResponse getStatus(UUID activationId) {
        Activation a = activationRepo.findById(activationId)
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVATION_NOT_FOUND));
        return new ActivationStatusResponse(
                a.getActivationId().toString(),
                a.getStatus().name(),
                a.getCardanoTxHash(),
                a.getExpiresAt().toEpochSecond(),
                a.getFailReason()
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Scheduled cleanup — chạy mỗi phút, mark expired

    @Transactional
    public void sweepExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Activation> expired = activationRepo.findExpired(ActivationStatus.PENDING_PAYMENT, now);
        for (Activation a : expired) {
            a.setStatus(ActivationStatus.EXPIRED);
            activationRepo.save(a);
            if (a.getGenieDid() != null) {
                genieRepo.decrementLoad(a.getGenieDid());
            }
            eventBus.publish(a.getActivationId().toString(),
                    new ActivationEvent("expired",
                            new ActivationEvent.Payload("EXPIRED", null, null)));
            log.info("Activation {} expired", a.getActivationId());
        }
    }

    private String generatePaymentReference(UUID activationId) {
        // Format: PK<8-char activation prefix><6-char random>
        String prefix = activationId.toString().replace("-", "").substring(0, 8).toUpperCase();
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PK" + prefix + rand;
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= 12 ? s : s.substring(0, 12) + "...";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
