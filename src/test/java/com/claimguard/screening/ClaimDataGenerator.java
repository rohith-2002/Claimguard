package com.claimguard.screening;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Seeded synthetic claim generator (no dependencies, plain JSON lines).
 *
 * <pre>mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.claimguard.screening.ClaimDataGenerator</pre>
 *
 * Writes data/claims.jsonl ordered by reportedAt. The "label" field lists the injected patterns and is
 * ignored by the service. A label is only set on claims EXPECTED to trigger that rule (e.g. the third
 * claim of a repeat-policy burst, the 11th and 12th claim of a provider spike).
 */
public final class ClaimDataGenerator {

    private static final long SEED = 42L;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private record Row(String claimId, String policy, LocalDate policyStart, long sumInsured, String lob,
                       String vehicle, String provider, String mobile, LocalDate loss, Instant reportedAt,
                       long claimed, List<String> labels) {

        Row withPolicy(String newPolicy, LocalDate newStart) {
            return new Row(claimId, newPolicy, newStart, sumInsured, lob, vehicle, provider, mobile, loss,
                    reportedAt, claimed, labels);
        }

        Row withVehicle(String newVehicle) {
            return new Row(claimId, policy, policyStart, sumInsured, lob, newVehicle, provider, mobile, loss,
                    reportedAt, claimed, labels);
        }

        Row withLabels(List<String> newLabels) {
            return new Row(claimId, policy, policyStart, sumInsured, lob, vehicle, provider, mobile, loss,
                    reportedAt, claimed, newLabels);
        }
    }

    /** What to inject into one claim. Pattern names double as labels. */
    private static final class Spec {
        boolean early;
        boolean high;
        boolean late;
        Boolean motor;
        LocalDate loss;
        Integer lag;
        String provider;
        List<String> labels;

        Spec(String... patterns) {
            this.labels = List.of(patterns);
            for (String p : patterns) {
                switch (p) {
                    case "EARLY_CLAIM" -> early = true;
                    case "HIGH_AMOUNT_RATIO" -> high = true;
                    case "LATE_REPORTING" -> late = true;
                    default -> { }
                }
            }
        }

        Spec motor(boolean v) { this.motor = v; return this; }
        Spec loss(LocalDate v) { this.loss = v; return this; }
        Spec lag(int v) { this.lag = v; return this; }
        Spec provider(String v) { this.provider = v; return this; }
    }

    private final Random rnd = new Random(SEED);
    private int seq = 0;

    public static void main(String[] args) throws IOException {
        generate(Path.of(args.length > 0 ? args[0] : "data/claims.jsonl"));
    }

    public static void generate(Path out) throws IOException {
        List<Row> rows = new ClaimDataGenerator().build();
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(out)) {
            for (Row row : rows) {
                writer.write(toJson(row));
                writer.newLine();
            }
        }
        Map<String, Integer> injected = new TreeMap<>();
        long labelled = rows.stream().filter(r -> !r.labels().isEmpty()).count();
        rows.forEach(r -> r.labels().forEach(l -> injected.merge(l, 1, Integer::sum)));
        System.out.printf("Wrote %d claims to %s (%d labelled = %.1f%%)%n",
                rows.size(), out, labelled, 100.0 * labelled / rows.size());
        injected.forEach((label, n) -> System.out.printf("  injected %-20s %d%n", label, n));
    }

    private List<Row> build() {
        List<Row> rows = new ArrayList<>();

        for (int i = 0; i < 480; i++) {
            rows.add(row(new Spec()));
        }
        for (int i = 0; i < 25; i++) {
            rows.add(row(new Spec("EARLY_CLAIM")));
            rows.add(row(new Spec("HIGH_AMOUNT_RATIO")));
            rows.add(row(new Spec("LATE_REPORTING")));
        }
        for (int i = 0; i < 15; i++) {
            rows.add(row(new Spec("EARLY_CLAIM", "HIGH_AMOUNT_RATIO")));
        }
        for (int i = 0; i < 10; i++) {
            rows.add(row(new Spec("HIGH_AMOUNT_RATIO", "LATE_REPORTING")));
            rows.add(row(new Spec("EARLY_CLAIM", "LATE_REPORTING")));
        }

        // REPEAT_POLICY_CLAIM: three claims on one policy 15-30 days apart; the third is labelled
        for (int i = 0; i < 12; i++) {
            Row first = row(new Spec());
            LocalDate secondLoss = first.loss().plusDays(15 + rnd.nextInt(16));
            Row second = row(new Spec().loss(secondLoss)).withPolicy(first.policy(), first.policyStart());
            LocalDate thirdLoss = secondLoss.plusDays(15 + rnd.nextInt(16));
            Row third = row(new Spec("REPEAT_POLICY_CLAIM").loss(thirdLoss))
                    .withPolicy(first.policy(), first.policyStart());
            rows.add(first);
            rows.add(second);
            rows.add(third);
        }

        // REPEAT_VEHICLE: same vehicle on a different policy 20-89 days later; the second is labelled
        for (int i = 0; i < 15; i++) {
            Row first = row(new Spec().motor(true));
            LocalDate secondLoss = first.loss().plusDays(30 + rnd.nextInt(50));
            Row second = row(new Spec("REPEAT_VEHICLE").motor(true).loss(secondLoss))
                    .withVehicle(first.vehicle());
            rows.add(first);
            rows.add(second);
        }

        // PROVIDER_SPIKE: 12 claims on one garage inside ~6 days; the 11th and 12th (by reportedAt) are labelled
        for (int p = 0; p < 3; p++) {
            String provider = "GAR-9" + p;
            LocalDate day0 = START.plusDays(40 + rnd.nextInt(200));
            List<Row> burst = new ArrayList<>();
            for (int k = 0; k < 12; k++) {
                burst.add(row(new Spec().motor(true).provider(provider)
                        .loss(day0.plusDays(k / 3)).lag(rnd.nextInt(3))));
            }
            burst.sort(Comparator.comparing(Row::reportedAt).thenComparing(Row::claimId));
            for (int k = 10; k < 12; k++) {
                burst.set(k, burst.get(k).withLabels(List.of("PROVIDER_SPIKE")));
            }
            rows.addAll(burst);
        }

        rows.sort(Comparator.comparing(Row::reportedAt).thenComparing(Row::claimId));
        return rows;
    }

    private Row row(Spec s) {
        int n = ++seq;
        boolean motor = s.motor != null ? s.motor : rnd.nextInt(100) < 60;
        LocalDate loss = s.loss != null ? s.loss : START.plusDays(60 + rnd.nextInt(240));
        LocalDate policyStart = s.early
                ? loss.minusDays(2 + rnd.nextInt(24))            // 2..25 days before loss
                : loss.minusDays(60 + rnd.nextInt(640));         // 60..699 days before loss
        long sumInsured = (200 + rnd.nextInt(1800)) * 1000L;
        double ratio = s.high ? 0.85 + rnd.nextInt(15) / 100.0 : 0.05 + rnd.nextInt(50) / 100.0;
        long claimed = Math.round(sumInsured * ratio);
        int lag = s.lag != null ? s.lag : (s.late ? 20 + rnd.nextInt(41) : rnd.nextInt(11));
        Instant reportedAt = loss.plusDays(lag).atTime(8 + rnd.nextInt(10), rnd.nextInt(60)).toInstant(ZoneOffset.UTC);
        String provider = s.provider != null
                ? s.provider
                : (motor ? "GAR-" : "HOS-") + String.format("%02d", 1 + rnd.nextInt(motor ? 25 : 15));
        String mobile = String.format("%010d", 6_000_000_000L + (long) (rnd.nextDouble() * 3_999_999_999L));
        return new Row(String.format("CLM-%05d", n), String.format("POL-%05d", n), policyStart, sumInsured,
                motor ? "MOTOR" : "HEALTH", motor ? String.format("KA01AB%04d", n) : null, provider, mobile,
                loss, reportedAt, claimed, s.labels);
    }

    private static String toJson(Row r) {
        String vehicle = r.vehicle() == null ? "" : "\"vehicleRegNo\":\"" + r.vehicle() + "\",";
        String labels = r.labels().stream().map(l -> "\"" + l + "\"").collect(Collectors.joining(",", "[", "]"));
        return "{\"claimId\":\"" + r.claimId() + "\",\"policyNumber\":\"" + r.policy()
                + "\",\"policyStartDate\":\"" + r.policyStart() + "\",\"sumInsured\":" + r.sumInsured()
                + ",\"lineOfBusiness\":\"" + r.lob() + "\"," + vehicle
                + "\"providerId\":\"" + r.provider() + "\",\"claimantMobile\":\"" + r.mobile()
                + "\",\"lossDate\":\"" + r.loss() + "\",\"reportedAt\":\"" + r.reportedAt()
                + "\",\"claimedAmount\":" + r.claimed() + ",\"label\":" + labels + "}";
    }
}
