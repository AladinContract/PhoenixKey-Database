package com.magiclamp.phoenixkey_db.service.username;

import com.magiclamp.phoenixkey_db.domain.User;
import com.magiclamp.phoenixkey_db.dto.username.UsernameResolveResponse;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetRequest;
import com.magiclamp.phoenixkey_db.dto.username.UsernameSetResponse;
import com.magiclamp.phoenixkey_db.exception.AppException;
import com.magiclamp.phoenixkey_db.exception.ErrorCode;
import com.magiclamp.phoenixkey_db.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsernameServiceImpl implements UsernameService {

    private final UserRepository userRepository;

    /** Cooldown 30 ngày sau khi đặt username lần đầu. */
    private static final int COOLDOWN_DAYS = 30;

    /**
     * Reserved names — không cho user đặt để tránh impersonation.
     * Lowercase hết vì username được normalize trước khi compare.
     */
    private static final Set<String> RESERVED = Set.of(
            "admin", "system", "root", "support", "help", "official",
            "phoenixkey", "magiclamp", "aladin", "orilife", "proofchat",
            "security", "wallet", "cardano", "blockchain", "crypto",
            "moderator", "staff", "team", "bot", "api", "auth",
            "null", "undefined", "anonymous", "guest", "user",
            "test", "demo", "sample", "example"
    );

    @Override
    @Transactional
    public UsernameSetResponse setUsername(String userDid, UsernameSetRequest request) {
        // 1. Normalize: lowercase + trim (username không phân biệt hoa thường)
        String normalized = request.username().toLowerCase().trim();

        // 2. Reserved name check
        if (RESERVED.contains(normalized)) {
            throw new AppException(ErrorCode.USERNAME_RESERVED,
                    "Username '" + normalized + "' là tên hệ thống, không được phép sử dụng");
        }

        // 3. Load user
        User user = userRepository.findByUserDid(userDid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, userDid));

        // 4. Cooldown check (chỉ áp dụng khi ĐÃ có username, không áp dụng lần đầu)
        boolean isFirstTime = user.getUsername() == null;
        if (!isFirstTime && user.getUsernameSetAt() != null) {
            OffsetDateTime changeableAfter = user.getUsernameSetAt().plusDays(COOLDOWN_DAYS);
            if (OffsetDateTime.now().isBefore(changeableAfter)) {
                throw new AppException(ErrorCode.USERNAME_COOLDOWN,
                        "Chỉ có thể đổi username sau: " + changeableAfter);
            }
        }

        // 5. Uniqueness check (DB constraint đã handle, nhưng check trước để có message rõ hơn)
        if (!normalized.equals(user.getUsername())) {
            userRepository.findByUsernameLower(normalized).ifPresent(existing -> {
                throw new AppException(ErrorCode.USERNAME_TAKEN,
                        "Username '" + normalized + "' đã được sử dụng");
            });
        }

        // 6. Lưu
        OffsetDateTime now = OffsetDateTime.now();
        user.setUsername(normalized);
        user.setUsernameSetAt(now);
        userRepository.save(user);

        log.info("Username set: did={} username={} firstTime={}", userDid, normalized, isFirstTime);

        // 7. Tính changeableAfter (null nếu lần đầu)
        OffsetDateTime changeableAfter = isFirstTime ? null : now.plusDays(COOLDOWN_DAYS);

        return new UsernameSetResponse(normalized, now, changeableAfter);
    }

    @Override
    @Transactional(readOnly = true)
    public UsernameResolveResponse resolveByUsername(String username) {
        String normalized = username.toLowerCase().trim();

        User user = userRepository.findByUsernameLower(normalized)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND,
                        "Username '" + normalized + "' không tồn tại"));

        return new UsernameResolveResponse(user.getUsername(), user.getUserDid());
    }
}
