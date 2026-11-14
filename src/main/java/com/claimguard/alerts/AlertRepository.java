package com.claimguard.alerts;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertRepository extends JpaRepository<AlertEntity, Long>, JpaSpecificationExecutor<AlertEntity> {

    boolean existsByClaimId(String claimId);

    Optional<AlertEntity> findByClaimId(String claimId);
}
