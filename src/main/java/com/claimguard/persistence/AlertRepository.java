package com.claimguard.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertRepository extends JpaRepository<AlertEntity, Long>, JpaSpecificationExecutor<AlertEntity> {

    boolean existsByClaimId(String claimId);
}
