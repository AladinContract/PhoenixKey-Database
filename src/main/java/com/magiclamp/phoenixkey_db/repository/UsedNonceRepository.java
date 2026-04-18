package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.UsedNonce;
import com.magiclamp.phoenixkey_db.domain.UsedNonceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * [V1.5] Repository cho {@link com.magiclamp.phoenixkey_db.domain.UsedNonce}.
 *
 * Chỉ INSERT/SELECT/DELETE — không UPDATE.
 */
@Repository
public interface UsedNonceRepository extends JpaRepository<UsedNonce, UsedNonceId> {

    /**
     * Kiểm tra nonce đã được sử dụng chưa.
     *
     * @param nonce   nonce từ signing request
     * @param userDid DID của user ký
     * @return true nếu đã tồn tại
     */
    boolean existsByNonceAndUserDid(String nonce, String userDid);

    /**
     * Xóa tất cả nonce đã hết hạn.
     * Dùng cho cronjob cleanup.
     *
     * @param now thời điểm hiện tại
     * @return số dòng đã xóa
     */
    @Modifying
    @Query("DELETE FROM UsedNonce n WHERE n.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
