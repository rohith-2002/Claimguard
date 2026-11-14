package com.claimguard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimguard.persistence.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertApiTest {

    @Autowired MockMvc mvc;
    @Autowired AlertRepository alerts;

    @BeforeEach
    void cleanAlerts() {
        alerts.deleteAll();
    }

    /** loss 6 days after start, claimed 90% of sum insured; reportedAt decides the LATE rule. */
    private static String claimJson(String id, String lob, String reportedAt, long claimed, String mobile) {
        String vehicle = "MOTOR".equals(lob) ? "\"vehicleRegNo\":\"KA01AB1234\"," : "";
        return """
                {"claimId":"%s","policyNumber":"POL-%s","policyStartDate":"2026-01-01","sumInsured":500000,
                 "lineOfBusiness":"%s",%s"providerId":"PRV-1","claimantMobile":"%s",
                 "lossDate":"2026-01-07","reportedAt":"%s","claimedAmount":%d}
                """.formatted(id, id, lob, vehicle, mobile, reportedAt, claimed);
    }

    private void screen(String json) throws Exception {
        mvc.perform(post("/api/claims/screen").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void screenThenAlertIsPersistedAndVisibleInQueueWithMaskedMobile() throws Exception {
        mvc.perform(post("/api/claims/screen").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("API-1", "MOTOR", "2026-02-01T10:00:00Z", 450_000, "9876543210")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value("API-1"))
                .andExpect(jsonPath("$.score").value(85))
                .andExpect(jsonPath("$.alertRaised").value(true))
                .andExpect(jsonPath("$.triggeredRules.length()").value(3))
                .andExpect(jsonPath("$.triggeredRules[0].reason").isNotEmpty());

        String body = mvc.perform(get("/api/alerts?status=OPEN&lineOfBusiness=MOTOR&minScore=40&page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[*].claimId", hasItem("API-1")))
                .andExpect(jsonPath("$.items[?(@.claimId=='API-1')].claimantMobile", hasItem("******3210")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("9876543210");
    }

    @Test
    void invalidClaimReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/claims/screen").contentType(MediaType.APPLICATION_JSON).content("{\"claimId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimId").exists())
                .andExpect(jsonPath("$.fieldErrors.policyNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.claimedAmount").exists());
    }

    @Test
    void motorClaimWithoutVehicleRegNoReturns400() throws Exception {
        String json = claimJson("API-2", "MOTOR", "2026-02-01T10:00:00Z", 450_000, "9876543210")
                .replace("\"vehicleRegNo\":\"KA01AB1234\",", "");
        mvc.perform(post("/api/claims/screen").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleRegNoValid").exists());
    }

    @Test
    void badMobileFormatReturns400WithoutEchoingTheValue() throws Exception {
        String body = mvc.perform(post("/api/claims/screen").contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("API-3", "MOTOR", "2026-02-01T10:00:00Z", 450_000, "12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.claimantMobile").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("12345");
    }

    @Test
    void queueIsSortedByScoreDescThenNewestFirst() throws Exception {
        screen(claimJson("S1", "MOTOR", "2026-02-01T10:00:00Z", 450_000, "9000000001")); // 85, older
        screen(claimJson("S2", "MOTOR", "2026-02-15T10:00:00Z", 100_000, "9000000002")); // 50 (early + late)
        screen(claimJson("S3", "MOTOR", "2026-03-01T10:00:00Z", 450_000, "9000000003")); // 85, newer

        mvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[*].claimId", contains("S3", "S1", "S2")));
    }

    @Test
    void filtersByLineOfBusinessAndMinScoreAndPaginates() throws Exception {
        screen(claimJson("F1", "MOTOR", "2026-02-01T10:00:00Z", 450_000, "9000000001")); // 85
        screen(claimJson("F2", "HEALTH", "2026-02-01T10:00:00Z", 450_000, "9000000002")); // 85
        screen(claimJson("F3", "HEALTH", "2026-02-15T10:00:00Z", 100_000, "9000000003")); // 50

        mvc.perform(get("/api/alerts?lineOfBusiness=HEALTH&minScore=60"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].claimId").value("F2"));

        mvc.perform(get("/api/alerts?page=0&size=2"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void invalidQueryParametersReturn400() throws Exception {
        mvc.perform(get("/api/alerts?size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/alerts?status=NOPE")).andExpect(status().isBadRequest());
    }
}
