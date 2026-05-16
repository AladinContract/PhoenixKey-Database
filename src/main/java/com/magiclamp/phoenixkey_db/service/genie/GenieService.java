package com.magiclamp.phoenixkey_db.service.genie;

import com.magiclamp.phoenixkey_db.domain.Genie;
import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.GenieRepository;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import com.magiclamp.phoenixkey_db.service.cardano.BlockfrostHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Genie operator registry. Genies are PhoenixKey users who additionally
 * volunteer to facilitate activation packages (off-chain payment ↔ on-chain
 * LAMP+ADA transfer).
 *
 * <p>A user can register as a Genie if their wallet holds ≥ min_balance_lamp
 * (1001) LAMP and ≥ min_balance_lovelace (10 ADA). Status downgrades to
 * OFFLINE if last_seen > 5 min ago.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenieService {

    private final GenieRepository genieRepo;
    private final UserRepository userRepo;
    private final BlockfrostHttpClient blockfrost;

    @Value("${phoenixkey.genie.min-balance-lamp:1001}")
    private long minBalanceLamp;

    @Value("${phoenixkey.genie.min-balance-lovelace:10000000}")
    private long minBalanceLovelace;

    @Value("${phoenixkey.genie.max-concurrent:3}")
    private int maxConcurrent;

    @Value("${phoenixkey.genie.offline-after-minutes:5}")
    private int offlineAfterMinutes;

    @Transactional
    public void register(String genieDid) {
        User user = userRepo.findByUserDid(genieDid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getWalletAddress() == null) {
            throw new AppException(ErrorCode.WALLET_NOT_REGISTERED);
        }

        // Verify on-chain balance meets minimum
        var utxos = blockfrost.getAddressUtxos(user.getWalletAddress());
        if (utxos.lampQuantity() < minBalanceLamp || utxos.lovelace() < minBalanceLovelace) {
            throw new AppException(ErrorCode.GENIE_INSUFFICIENT_BALANCE,
                    "Cần ≥ " + minBalanceLamp + " LAMP và "
                            + (minBalanceLovelace / 1_000_000) + " ADA. Hiện có: "
                            + utxos.lampQuantity() + " LAMP, "
                            + (utxos.lovelace() / 1_000_000) + " ADA.");
        }

        Genie genie = genieRepo.findById(genieDid).orElse(
                Genie.builder()
                        .genieDid(genieDid)
                        .walletAddress(user.getWalletAddress())
                        .minBalanceLamp(minBalanceLamp)
                        .minBalanceLovelace(minBalanceLovelace)
                        .currentActivations(0)
                        .maxConcurrent(maxConcurrent)
                        .registeredAt(OffsetDateTime.now())
                        .build()
        );
        genie.setStatus(Genie.GenieStatus.AVAILABLE);
        genie.setLastSeenAt(OffsetDateTime.now());
        genie.setWalletAddress(user.getWalletAddress());
        genieRepo.save(genie);

        log.info("Genie registered: {}", genieDid);
    }

    @Transactional
    public void heartbeat(String genieDid) {
        genieRepo.findById(genieDid).ifPresent(g -> {
            g.setLastSeenAt(OffsetDateTime.now());
            if (g.getStatus() == Genie.GenieStatus.OFFLINE) {
                g.setStatus(Genie.GenieStatus.AVAILABLE);
            }
            genieRepo.save(g);
        });
    }

    /** Periodically demote stale Genies to OFFLINE. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void sweepOffline() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(Duration.ofMinutes(offlineAfterMinutes));
        var stale = genieRepo.findStale(cutoff);
        for (Genie g : stale) {
            g.setStatus(Genie.GenieStatus.OFFLINE);
            genieRepo.save(g);
        }
        if (!stale.isEmpty()) {
            log.info("Marked {} Genie(s) as OFFLINE", stale.size());
        }
    }
}
