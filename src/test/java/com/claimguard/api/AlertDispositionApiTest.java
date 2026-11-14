package com.claimguard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimguard.alerts.AlertAuditEntity;
import com.claimguard.alerts.AlertAuditRepository;
import com.claimguard.alerts.AlertEntity;
import com.claimguard.alerts.AlertRepository;
import com.claimguard.alerts.AlertStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** PATCH /api/alerts/{id}: OPEN -> CONFIRMED_FRAUD/FALSE_POSITIVE, mandatory note, append-only audit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertDispositionApiTest {

    @Autowired MockMvc mvc;
    @Autowired AlertRepository alerts;
    @Autowired AlertAuditRepository audit;

    @BeforeEach
    void cleanAlerts() {
        audit.deleteAll();
        alerts.deleteAll();
    }

    private static String claimJson(String id) {
        return """
                {"claimId":"%s","policyNumber":"POL-%s","policyStartDate":"2026-01-01","sumInsured":500000,
                 "lineOfBusiness":"MOTOR","vehicleRegNo":"KA01AB%s","providerId":"PRV-1",
                 "claimantMobile":"9876543210","lossDate":"2026-01-07","reportedAt":"2026-02-01T10:00:00Z",
                 "claimedAmount":450000}
                """.formatted(id, id, id);
    }

    /** Screens a high-scoring claim so an OPEN alert exists, and returns it. */
    private AlertEntity screenOpenAlert(String id) throws Exception {
        mvc.perform(post("/api/claims/screen").contentType(MediaType.APPLICATION_JSON).content(claimJson(id)))
                .andExpect(status().isOk());
        return alerts.findByClaimId(id).orElseThrow();
    }

    private static String disposition(String status, String note, String investigator) {
        return """
                {"status":"%s","note":"%s","investigator":"%s"}
                """.formatted(status, note, investigator);
    }

    @Test
    void confirmingClosesTheAlertAndWritesOneAuditRow() throws Exception {
        AlertEntity alert = screenOpenAlert("DISP-1");

        mvc.perform(patch("/api/alerts/" + alert.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposition("CONFIRMED_FRAUD", "Staged damage confirmed", "inv-anna")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_FRAUD"))
                .andExpect(jsonPath("$.claimId").value("DISP-1"));

        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.CONFIRMED_FRAUD);

        List<AlertAuditEntity> rows = audit.findByAlertIdOrderByIdAsc(alert.getId());
        assertThat(rows).hasSize(1);
        AlertAuditEntity row = rows.get(0);
        assertThat(row.getFromStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(row.getToStatus()).isEqualTo(AlertStatus.CONFIRMED_FRAUD);
        assertThat(row.getInvestigator()).isEqualTo("inv-anna");
        assertThat(row.getNote()).isEqualTo("Staged damage confirmed");
        assertThat(row.getChangedAt()).isNotNull();
    }

    @Test
    void falsePositiveClosesTheAlertAndAuditsTheDecision() throws Exception {
        AlertEntity alert = screenOpenAlert("DISP-2");

        mvc.perform(patch("/api/alerts/" + alert.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposition("FALSE_POSITIVE", "Genuine repair invoice attached", "inv-bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"));

        List<AlertAuditEntity> rows = audit.findByAlertIdOrderByIdAsc(alert.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getToStatus()).isEqualTo(AlertStatus.FALSE_POSITIVE);
    }

    @Test
    void aBlankNoteIsRejectedWith400() throws Exception {
        AlertEntity alert = screenOpenAlert("DISP-3");

        mvc.perform(patch("/api/alerts/" + alert.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposition("CONFIRMED_FRAUD", "   ", "inv-anna")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").exists());

        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(audit.findByAlertIdOrderByIdAsc(alert.getId())).isEmpty();
    }

    @Test
    void anAlreadyClosedAlertCannotChangeAgainAndReturns409() throws Exception {
        AlertEntity alert = screenOpenAlert("DISP-4");
        String path = "/api/alerts/" + alert.getId();

        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content(disposition("CONFIRMED_FRAUD", "First decision", "inv-anna")))
                .andExpect(status().isOk());

        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content(disposition("FALSE_POSITIVE", "Trying to reopen", "inv-bob")))
                .andExpect(status().isConflict());

        // The first decision stands and there is exactly one audit row.
        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertStatus.CONFIRMED_FRAUD);
        assertThat(audit.findByAlertIdOrderByIdAsc(alert.getId())).hasSize(1);
    }

    @Test
    void anUnknownAlertReturns404() throws Exception {
        mvc.perform(patch("/api/alerts/999999").contentType(MediaType.APPLICATION_JSON)
                        .content(disposition("CONFIRMED_FRAUD", "No such alert", "inv-anna")))
                .andExpect(status().isNotFound());
    }

    @Test
    void reopeningWithStatusOpenIsRejectedWith400() throws Exception {
        AlertEntity alert = screenOpenAlert("DISP-5");

        mvc.perform(patch("/api/alerts/" + alert.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disposition("OPEN", "Stay open", "inv-anna")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.closableStatus").exists());

        assertThat(alerts.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(AlertStatus.OPEN);
    }
}
