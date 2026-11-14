package com.claimguard.alerts;

import com.claimguard.api.AlertView;
import com.claimguard.api.DispositionRequest;
import com.claimguard.api.PageResponse;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertService {

    private static final Sort QUEUE_ORDER = Sort.by(
            Sort.Order.desc("score"), Sort.Order.desc("reportedAt"), Sort.Order.desc("id"));

    private final AlertRepository alerts;
    private final AlertAuditRepository audit;

    public AlertService(AlertRepository alerts, AlertAuditRepository audit) {
        this.alerts = alerts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertView> search(AlertStatus status, LineOfBusiness lineOfBusiness,
                                          Integer minScore, int page, int size) {
        Specification<AlertEntity> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (lineOfBusiness != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("lineOfBusiness"), lineOfBusiness));
        }
        if (minScore != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("score"), minScore));
        }
        Pageable pageable = PageRequest.of(page, size, QUEUE_ORDER);
        Page<AlertEntity> result = alerts.findAll(spec, pageable);
        return new PageResponse<>(result.map(AlertView::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Records an investigator decision. Only an OPEN alert can be closed, and the status change plus its
     * append-only audit row are written in the same transaction.
     */
    @Transactional
    public AlertView dispose(Long id, DispositionRequest request) {
        AlertEntity alert = alerts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No alert with id " + id));
        AlertStatus from = alert.getStatus();
        if (from != AlertStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alert " + id + " is already " + from);
        }
        alert.close(request.status());
        AlertEntity saved = alerts.save(alert);
        audit.save(AlertAuditEntity.of(saved.getId(), request.investigator(), Instant.now(), from,
                request.status(), request.note()));
        return AlertView.from(saved);
    }
}
