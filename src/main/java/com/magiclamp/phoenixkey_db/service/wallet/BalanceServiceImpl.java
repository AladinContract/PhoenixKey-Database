package com.magiclamp.phoenixkey_db.service.wallet;

import com.magiclamp.phoenixkey_db.domain.MagicClaim;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.wallet.WalletDtos.BalanceResponse;
import com.magiclamp.phoenixkey_db.dto.wallet.WalletDtos.MagicClaimResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.MagicClaimRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.cardano.BlockfrostHttpClient;
import com.magiclamp.phoenixkey_db.service.cardano.MagicMintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceServiceImpl implements BalanceService {

    private final UserRepository userRepo;
    private final MagicClaimRepository claimRepo;
    private final BlockfrostHttpClient blockfrost;
    private final MagicMintService magicMintService;

    @Value("${phoenixkey.magic.rate-per-lamp-per-slot:0.0001}")
    private String ratePerLampPerSlot;

    @Value("${phoenixkey.magic.min-claim-amount:1}")
    private long minClaimAmount;

    // ─── Balance ──────────────────────────────────────────────────

    @Override
    public BalanceResponse getBalance(String userDid) {
        User user = userRepo.findByUserDid(userDid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String address = user.getWalletAddress();
        if (address == null) {
            // User registered but hasn't published wallet address yet
            return new BalanceResponse(null, 0, 0, 0, 0,
                    ratePerLampPerSlot, 0, blockfrost.getCurrentSlot());
        }

        var utxos = blockfrost.getAddressUtxos(address);
        long currentSlot = blockfrost.getCurrentSlot();
        long lastAccrualSlot = user.getLastAccrualSlot() != null ? user.getLastAccrualSlot() : currentSlot;
        long accrued = computeAccruedMagic(utxos.lampQuantity(), lastAccrualSlot, currentSlot);

        return new BalanceResponse(
                address,
                utxos.lovelace(),
                utxos.lampQuantity(),
                utxos.magicQuantity(),
                accrued,
                ratePerLampPerSlot,
                lastAccrualSlot,
                currentSlot
        );
    }

    private long computeAccruedMagic(long lampBalance, long fromSlot, long toSlot) {
        if (lampBalance <= 0 || toSlot <= fromSlot) return 0;
        BigDecimal rate = new BigDecimal(ratePerLampPerSlot);
        BigDecimal slots = BigDecimal.valueOf(toSlot - fromSlot);
        BigDecimal lamp = BigDecimal.valueOf(lampBalance);
        return rate.multiply(slots).multiply(lamp)
                .setScale(0, RoundingMode.FLOOR)
                .longValue();
    }

    // ─── Wallet registration ──────────────────────────────────────

    @Override
    @Transactional
    public void registerWalletAddress(String userDid, String walletAddress) {
        User user = userRepo.findByUserDid(userDid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getWalletAddress() != null && !user.getWalletAddress().equals(walletAddress)) {
            log.warn("Wallet address changed for {}: old={}, new={}",
                    userDid, user.getWalletAddress(), walletAddress);
            // Allow change but audit it — server is non-custodial, address is user's choice
        }

        user.setWalletAddress(walletAddress);
        if (user.getLastAccrualSlot() == null) {
            user.setLastAccrualSlot(blockfrost.getCurrentSlot());
        }
        userRepo.save(user);

        log.info("Wallet registered for {}: {}", userDid, walletAddress);
    }

    // ─── MAGIC claim ──────────────────────────────────────────────

    @Override
    @Transactional
    public MagicClaimResponse claimMagic(String userDid) {
        User user = userRepo.findByUserDid(userDid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getWalletAddress() == null) {
            throw new AppException(ErrorCode.WALLET_NOT_REGISTERED);
        }

        var utxos = blockfrost.getAddressUtxos(user.getWalletAddress());
        long currentSlot = blockfrost.getCurrentSlot();
        long fromSlot = user.getLastAccrualSlot() != null ? user.getLastAccrualSlot() : currentSlot;
        long amount = computeAccruedMagic(utxos.lampQuantity(), fromSlot, currentSlot);

        if (amount < minClaimAmount) {
            throw new AppException(ErrorCode.MAGIC_AMOUNT_TOO_SMALL,
                    "Tối thiểu " + minClaimAmount + " MAGIC để claim. Hiện có " + amount + ".");
        }

        // Record pending claim BEFORE submitting tx (idempotency anchor)
        MagicClaim claim = MagicClaim.builder()
                .claimId(UUID.randomUUID())
                .userDid(userDid)
                .amountMagic(amount)
                .claimedSlot(currentSlot)
                .status(MagicClaim.Status.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
        claimRepo.save(claim);

        // Mint and send MAGIC
        String txHash;
        try {
            txHash = magicMintService.mintAndSend(user.getWalletAddress(), amount);
        } catch (Exception e) {
            log.error("MAGIC mint failed for {}: {}", userDid, e.getMessage());
            claim.setStatus(MagicClaim.Status.FAILED);
            claim.setFailReason(e.getMessage());
            claimRepo.save(claim);
            throw new AppException(ErrorCode.CARDANO_TX_FAILED);
        }

        claim.setCardanoTxHash(txHash);
        claim.setStatus(MagicClaim.Status.SUBMITTED);
        claimRepo.save(claim);

        // Advance accrual cursor
        user.setLastAccrualSlot(currentSlot);
        userRepo.save(user);

        log.info("MAGIC claimed: user={}, amount={}, tx={}", userDid, amount, txHash);

        return new MagicClaimResponse(
                claim.getClaimId().toString(),
                amount,
                txHash,
                MagicClaim.Status.SUBMITTED.name()
        );
    }
}
