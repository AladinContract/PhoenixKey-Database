package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho {@link com.magiclamp.phoenixkey_db.domain.User}.
 *
 * <p>
 * Chỉ có 2 cách tra cứu hợp lệ:
 * <ul>
 * <li>Theo id (UUIDv7)</li>
 * <li>Theo user_did (DID string)</li>
 * </ul>
 * Không tra cứu theo email/SĐT ở đây — xem {@link AuthMethodRepository}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Tìm user qua DID string.
     *
     * @param userDid DID từ Blockchain (did:prism:... hoặc did:cardano:...)
     * @return Optional chứa User nếu tìm thấy
     */
    Optional<User> findByUserDid(String userDid);

    /**
     * Kiểm tra user có tồn tại qua DID không.
     *
     * @param userDid DID string
     * @return true nếu tồn tại
     */
    boolean existsByUserDid(String userDid);

    /**
     * Đếm số user đang trong trạng thái khôi phục.
     * Dùng cho monitoring/alerting.
     */
    @Query("SELECT COUNT(u) FROM User u " +
            "JOIN com.magiclamp.phoenixkey_db.domain.OnchainTaadStateCache c " +
            "ON u.userDid = c.userDid " +
            "WHERE c.status = 'RECOVERING'")
    long countRecoveringUsers();

    /**
     * Tìm user kèm eager load auth methods.
     * Chỉ dùng khi cần — tránh N+1 query.
     *
     * @param userDid DID string
     * @return Optional chứa User + authMethods
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.authMethods WHERE u.userDid = :userDid")
    Optional<User> findByUserDidWithAuthMethods(@Param("userDid") String userDid);
}
