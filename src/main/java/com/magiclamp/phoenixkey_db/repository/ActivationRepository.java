package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.Activation;
import com.magiclamp.phoenixkey_db.domain.ActivationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivationRepository extends JpaRepository<Activation, UUID> {

    List<Activation> findByUserDidOrderByCreatedAtDesc(String userDid);

    List<Activation> findByGenieDidAndStatusIn(String genieDid, List<ActivationStatus> statuses);

    @Query("SELECT a FROM Activation a WHERE a.status = :status AND a.expiresAt < :now")
    List<Activation> findExpired(@Param("status") ActivationStatus status,
                                  @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE Activation a SET a.status = :newStatus, a.failReason = :reason " +
           "WHERE a.activationId = :id AND a.status = :expectedStatus")
    int compareAndSetStatus(@Param("id") UUID id,
                            @Param("expectedStatus") ActivationStatus expectedStatus,
                            @Param("newStatus") ActivationStatus newStatus,
                            @Param("reason") String reason);
}
