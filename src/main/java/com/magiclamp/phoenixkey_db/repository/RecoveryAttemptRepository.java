package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.RecoveryAttempt;
import com.magiclamp.phoenixkey_db.domain.RecoveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecoveryAttemptRepository extends JpaRepository<RecoveryAttempt, UUID> {

    Optional<RecoveryAttempt> findFirstByUserDidAndStatusOrderByCreatedAtDesc(
            String userDid, RecoveryStatus status);

    List<RecoveryAttempt> findByUserDidOrderByCreatedAtDesc(String userDid);

    @Query("SELECT r FROM RecoveryAttempt r WHERE r.status = :status AND r.deadlineSlot < :currentSlot")
    List<RecoveryAttempt> findFinalizable(@Param("status") RecoveryStatus status,
                                           @Param("currentSlot") long currentSlot);
}
