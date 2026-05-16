package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.Genie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GenieRepository extends JpaRepository<Genie, String> {

    @Query("SELECT g FROM Genie g WHERE g.status <> com.magiclamp.phoenixkey_db.domain.Genie.GenieStatus.OFFLINE " +
           "AND (g.lastSeenAt IS NULL OR g.lastSeenAt < :cutoff)")
    List<Genie> findStale(@Param("cutoff") OffsetDateTime cutoff);


    /**
     * Atomic claim: find a Genie with status=AVAILABLE và current_activations < max_concurrent,
     * increment counter atomically, return locked row. Native query để dùng SKIP LOCKED.
     */
    @Query(value = """
            SELECT * FROM genies
             WHERE status = 'AVAILABLE'
               AND current_activations < max_concurrent
             ORDER BY current_activations ASC, last_seen_at DESC
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Genie> claimAvailable();

    @Modifying
    @Query("UPDATE Genie g SET g.currentActivations = g.currentActivations + 1 WHERE g.genieDid = :did")
    void incrementLoad(@Param("did") String genieDid);

    @Modifying
    @Query("UPDATE Genie g SET g.currentActivations = GREATEST(0, g.currentActivations - 1) WHERE g.genieDid = :did")
    void decrementLoad(@Param("did") String genieDid);
}
