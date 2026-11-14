package com.claimguard.alerts;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertAuditRepository extends JpaRepository<AlertAuditEntity, Long> {

    List<AlertAuditEntity> findByAlertIdOrderByIdAsc(Long alertId);
}
