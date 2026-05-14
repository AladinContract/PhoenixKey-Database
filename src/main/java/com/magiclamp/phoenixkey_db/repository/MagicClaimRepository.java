package com.magiclamp.phoenixkey_db.repository;

import com.magiclamp.phoenixkey_db.domain.MagicClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MagicClaimRepository extends JpaRepository<MagicClaim, UUID> {
    List<MagicClaim> findByUserDidOrderByCreatedAtDesc(String userDid);
}
