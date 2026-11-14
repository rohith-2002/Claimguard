package com.claimguard.alerts;

import com.claimguard.api.dto.AlertView;
import com.claimguard.api.dto.PageResponse;
import com.claimguard.domain.AlertStatus;
import com.claimguard.domain.LineOfBusiness;
import com.claimguard.persistence.AlertEntity;
import com.claimguard.persistence.AlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private static final Sort QUEUE_ORDER = Sort.by(
            Sort.Order.desc("score"), Sort.Order.desc("reportedAt"), Sort.Order.desc("id"));

    private final AlertRepository alerts;

    public AlertService(AlertRepository alerts) {
        this.alerts = alerts;
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
}
