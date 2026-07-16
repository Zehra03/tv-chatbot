package com.paximum.paxassist.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One-time, opt-in harness for the Validator cost/benefit question: "how much does adding the
 * second (local Ollama) validator LLM cost in latency and $, on top of the main LLM call that
 * would happen anyway?"
 *
 * Runs the same fixed 50 messages (src/test/resources/validator-ab-harness-messages.txt) through
 * the real {@code chatClient} bean once per message, then additionally through the real
 * {@link ValidatorService} bean, and writes a per-message CSV plus a console summary comparing:
 *   - Scenario A (validator off)  : main LLM latency + cost only.
 *   - Scenario B (validator on)   : main LLM latency + cost, PLUS validator latency + cost.
 *
 * Not part of the normal `mvn test` run — gated behind -DrunValidatorAbHarness=true because it makes
 * 50 real LLM calls (main model + validator model) and takes minutes. It runs whatever provider the
 * active config selects: by default that is Gemini for the main chat and DeepSeek for the validator
 * (needs GEMINI_API_KEY + DEEPSEEK_API_KEY), i.e. real API cost per run — see the pricing knobs below.
 * Requires the full Spring context (same DB/Redis prerequisites as the rest of the suite) so it
 * exercises the exact same beans production does.
 *
 * Run with:
 *   mvn test -Dtest=ValidatorAbComparisonHarness -DrunValidatorAbHarness=true
 *
 * Main-LLM $ pricing below is a PLACEHOLDER (Gemini Flash-class list pricing at time of writing) —
 * confirm current pricing at ai.google.dev/pricing before treating the $ columns as authoritative.
 * Override via -DmainLlmPromptPricePer1kUsd=... -DmainLlmCompletionPricePer1kUsd=... if it has
 * changed. Token counts are printed regardless, so cost can always be recomputed with the right
 * price. The validator's $ column defaults to 0 — correct only for a local (no-marginal-cost)
 * validator provider. On the default DeepSeek validator, pass its real prices via
 * -DvalidatorPromptPricePer1kUsd / -DvalidatorCompletionPricePer1kUsd (see the constants below),
 * otherwise Scenario B understates the validator's cost as free.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "runValidatorAbHarness", matches = "true")
class ValidatorAbComparisonHarness {

    private static final double MAIN_LLM_PROMPT_PRICE_PER_1K_USD =
            Double.parseDouble(System.getProperty("mainLlmPromptPricePer1kUsd", "0.000075"));
    private static final double MAIN_LLM_COMPLETION_PRICE_PER_1K_USD =
            Double.parseDouble(System.getProperty("mainLlmCompletionPricePer1kUsd", "0.0003"));
    // Default 0 assumes a local validator (no marginal API cost) — NOT the default deepseek provider.
    // For cloud validator runs pass the provider's real prices, e.g. DeepSeek v4-flash:
    // -DvalidatorPromptPricePer1kUsd=0.00014
    // -DvalidatorCompletionPricePer1kUsd=0.00028 (cache-miss list prices — effective cost is lower
    // because the repeated system prompt hits DeepSeek's context cache at ~1/50th the price).
    private static final double VALIDATOR_PROMPT_PRICE_PER_1K_USD =
            Double.parseDouble(System.getProperty("validatorPromptPricePer1kUsd", "0"));
    private static final double VALIDATOR_COMPLETION_PRICE_PER_1K_USD =
            Double.parseDouble(System.getProperty("validatorCompletionPricePer1kUsd", "0"));

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ValidatorService validatorService;

    private record Row(int id, String message, String candidateAnswer, long mainLatencyMs, int mainPromptTokens,
                        int mainCompletionTokens, double mainCostUsd, long validatorLatencyMs,
                        int validatorPromptTokens, int validatorCompletionTokens, String verdict, String feedback,
                        Boolean expectedCorrect) {

        double scenarioATotalLatencyMs() {
            return mainLatencyMs;
        }

        double scenarioACostUsd() {
            return mainCostUsd;
        }

        double scenarioBTotalLatencyMs() {
            return mainLatencyMs + validatorLatencyMs;
        }

        double scenarioBCostUsd() {
            return mainCostUsd + validatorPromptTokens / 1000.0 * VALIDATOR_PROMPT_PRICE_PER_1K_USD
                    + validatorCompletionTokens / 1000.0 * VALIDATOR_COMPLETION_PRICE_PER_1K_USD;
        }

        /** Ground truth says this answer had no guardrail violation (fabrication/date/scope). */
        boolean groundTruthCorrect() {
            return Boolean.TRUE.equals(expectedCorrect);
        }

        /** Did the validator's APPROVED/REJECTED verdict match the ground-truth label? */
        boolean verdictMatchesGroundTruth() {
            if (expectedCorrect == null) {
                return false;
            }
            boolean approved = "APPROVED".equals(verdict);
            return approved == expectedCorrect;
        }
    }

    @Test
    void runAbComparison() throws IOException {
        List<String> messages = loadMessages();
        assertThat(messages).hasSize(50);
        Map<Integer, Boolean> groundTruth = loadGroundTruth();

        int limit = Integer.parseInt(System.getProperty("validatorAbHarnessLimit", String.valueOf(messages.size())));
        List<String> selected = messages.subList(0, Math.min(limit, messages.size()));
        // Inter-message pacing for rate-limited cloud providers (e.g. Groq free tier: 6k tokens/min
        // for qwen3-32b ≈ 3 validator calls/min). 0 = no pacing (local Ollama needs none).
        long delayMs = Long.parseLong(System.getProperty("validatorAbHarnessDelayMs", "0"));

        List<Row> rows = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0 && delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            int id = i + 1;
            Row row = runOne(id, selected.get(i), groundTruth.get(id));
            rows.add(row);
            if ("REJECTED".equals(row.verdict())) {
                System.out.printf(Locale.ROOT, "[%d] REJECTED — mesaj: %s%n    aday yanıt: %s%n    gerekçe: %s%n",
                        row.id(), row.message(), truncate(row.candidateAnswer(), 300), row.feedback());
            }
        }

        Path csvPath = writeCsv(rows);
        printSummary(rows, csvPath);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace("\n", " ").strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    private Row runOne(int id, String message, Boolean expectedCorrect) {
        long mainStart = System.nanoTime();
        ChatResponse mainResponse = chatClient.prompt().user(message).call().chatResponse();
        long mainLatencyMs = (System.nanoTime() - mainStart) / 1_000_000;

        String candidateAnswer = (mainResponse == null || mainResponse.getResult() == null
                || mainResponse.getResult().getOutput() == null)
                ? "" : mainResponse.getResult().getOutput().getText();
        Usage mainUsage = mainResponse == null || mainResponse.getMetadata() == null
                ? null : mainResponse.getMetadata().getUsage();
        int mainPromptTokens = mainUsage == null || mainUsage.getPromptTokens() == null
                ? 0 : mainUsage.getPromptTokens();
        int mainCompletionTokens = mainUsage == null || mainUsage.getCompletionTokens() == null
                ? 0 : mainUsage.getCompletionTokens();
        double mainCostUsd = mainPromptTokens / 1000.0 * MAIN_LLM_PROMPT_PRICE_PER_1K_USD
                + mainCompletionTokens / 1000.0 * MAIN_LLM_COMPLETION_PRICE_PER_1K_USD;

        ValidatorCallResult validatorResult = validatorService.validate(message, candidateAnswer, null);

        return new Row(id, message, candidateAnswer, mainLatencyMs, mainPromptTokens, mainCompletionTokens, mainCostUsd,
                validatorResult.metrics().latencyMs(), validatorResult.metrics().promptTokens(),
                validatorResult.metrics().completionTokens(), validatorResult.result().verdict().name(),
                validatorResult.result().feedback(), expectedCorrect);
    }

    /**
     * Ground-truth labels (src/test/resources/validator-ab-harness-ground-truth.csv) — manually
     * reviewed against the project's own guardrails (fabrication, date validity, scope/safety), the
     * same three criteria the validator is designed to check. Lets us score validator verdicts
     * against a fixed answer key instead of just eyeballing feedback text. Keyed by message id, so
     * it stays valid across different validator models as long as the main LLM's answers don't change
     * (main model runs at temperature 0.0, so they're effectively deterministic).
     */
    private Map<Integer, Boolean> loadGroundTruth() throws IOException {
        Map<Integer, Boolean> labels = new HashMap<>();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("validator-ab-harness-ground-truth.csv")) {
            if (in == null) {
                throw new IOException("validator-ab-harness-ground-truth.csv not found on classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine(); // header
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split(",", 3);
                    labels.put(Integer.parseInt(parts[0].strip()), Boolean.parseBoolean(parts[1].strip()));
                }
            }
        }
        return labels;
    }

    private List<String> loadMessages() throws IOException {
        List<String> messages = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("validator-ab-harness-messages.txt")) {
            if (in == null) {
                throw new IOException("validator-ab-harness-messages.txt not found on classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        messages.add(trimmed);
                    }
                }
            }
        }
        return messages;
    }

    private Path writeCsv(List<Row> rows) throws IOException {
        Path dir = Path.of("target", "reports");
        Files.createDirectories(dir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path path = dir.resolve("validator-ab-report-" + timestamp + ".csv");

        StringBuilder sb = new StringBuilder();
        sb.append("id,message,candidate_answer,main_latency_ms,main_prompt_tokens,main_completion_tokens,main_cost_usd,")
                .append("validator_latency_ms,validator_prompt_tokens,validator_completion_tokens,")
                .append("validator_verdict,validator_feedback,expected_correct,verdict_matches_ground_truth,")
                .append("scenario_a_latency_ms,scenario_a_cost_usd,")
                .append("scenario_b_latency_ms,scenario_b_cost_usd\n");
        for (Row r : rows) {
            sb.append(r.id()).append(',')
                    .append(csvEscape(r.message())).append(',')
                    .append(csvEscape(r.candidateAnswer())).append(',')
                    .append(r.mainLatencyMs()).append(',')
                    .append(r.mainPromptTokens()).append(',')
                    .append(r.mainCompletionTokens()).append(',')
                    .append(fmt(r.mainCostUsd())).append(',')
                    .append(r.validatorLatencyMs()).append(',')
                    .append(r.validatorPromptTokens()).append(',')
                    .append(r.validatorCompletionTokens()).append(',')
                    .append(r.verdict()).append(',')
                    .append(csvEscape(r.feedback())).append(',')
                    .append(r.expectedCorrect()).append(',')
                    .append(r.verdictMatchesGroundTruth()).append(',')
                    .append(fmt(r.scenarioATotalLatencyMs())).append(',')
                    .append(fmt(r.scenarioACostUsd())).append(',')
                    .append(fmt(r.scenarioBTotalLatencyMs())).append(',')
                    .append(fmt(r.scenarioBCostUsd())).append('\n');
        }
        try {
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    private void printSummary(List<Row> rows, Path csvPath) {
        int n = rows.size();
        double avgA = rows.stream().mapToDouble(Row::scenarioATotalLatencyMs).average().orElse(0);
        double avgB = rows.stream().mapToDouble(Row::scenarioBTotalLatencyMs).average().orElse(0);
        double totalA = rows.stream().mapToDouble(Row::scenarioATotalLatencyMs).sum();
        double totalB = rows.stream().mapToDouble(Row::scenarioBTotalLatencyMs).sum();
        double costA = rows.stream().mapToDouble(Row::scenarioACostUsd).sum();
        double costB = rows.stream().mapToDouble(Row::scenarioBCostUsd).sum();
        double avgValidatorLatency = rows.stream().mapToLong(Row::validatorLatencyMs).average().orElse(0);
        long rejected = rows.stream().filter(r -> "REJECTED".equals(r.verdict())).count();

        long labeled = rows.stream().filter(r -> r.expectedCorrect() != null).count();
        long groundTruthCorrectCount = rows.stream().filter(Row::groundTruthCorrect).count();
        long verdictMatchesCount = rows.stream().filter(Row::verdictMatchesGroundTruth).count();
        // Confusion-matrix breakdown against ground truth, for the REJECTED-heavy failure mode this
        // harness exists to catch: does the validator actually catch real violations (recall), and
        // how often does it wrongly reject a genuinely fine answer (false-reject rate)?
        long trueViolations = rows.stream().filter(r -> r.expectedCorrect() != null && !r.groundTruthCorrect()).count();
        long caughtViolations = rows.stream()
                .filter(r -> r.expectedCorrect() != null && !r.groundTruthCorrect() && "REJECTED".equals(r.verdict()))
                .count();
        long goodAnswers = rows.stream().filter(r -> r.expectedCorrect() != null && r.groundTruthCorrect()).count();
        long falseRejects = rows.stream()
                .filter(r -> r.expectedCorrect() != null && r.groundTruthCorrect() && "REJECTED".equals(r.verdict()))
                .count();

        System.out.println();
        System.out.println("==================== Validator A/B cost-benefit report (n=" + n + ") ====================");
        System.out.printf(Locale.ROOT, "Scenario A (validator OFF) — total latency: %.0f ms, avg: %.0f ms, cost: $%.6f%n",
                totalA, avgA, costA);
        System.out.printf(Locale.ROOT, "Scenario B (validator ON)  — total latency: %.0f ms, avg: %.0f ms, cost: $%.6f%n",
                totalB, avgB, costB);
        System.out.printf(Locale.ROOT, "Validator overhead         — avg latency added: %.0f ms/request (%.1f%%), extra $ cost: $%.6f%n",
                avgValidatorLatency, avgA == 0 ? 0 : (avgValidatorLatency / avgA) * 100.0, costB - costA);
        System.out.println("Validator verdicts — REJECTED: " + rejected + "/" + n + ", APPROVED: " + (n - rejected) + "/" + n);
        if (labeled > 0) {
            System.out.printf(Locale.ROOT,
                    "Scenario A doğruluk (ham çıktı, guardrail uyumu)  — %d/%d = %.1f%%%n",
                    groundTruthCorrectCount, labeled, 100.0 * groundTruthCorrectCount / labeled);
            System.out.printf(Locale.ROOT,
                    "Scenario B doğruluk (validator kararı doğruluğu) — %d/%d = %.1f%%%n",
                    verdictMatchesCount, labeled, 100.0 * verdictMatchesCount / labeled);
            if (trueViolations > 0) {
                System.out.printf(Locale.ROOT,
                        "  ↳ recall (gerçek ihlalleri yakalama oranı): %d/%d = %.1f%%%n",
                        caughtViolations, trueViolations, 100.0 * caughtViolations / trueViolations);
            }
            if (goodAnswers > 0) {
                System.out.printf(Locale.ROOT,
                        "  ↳ false-reject oranı (iyi cevabı da reddetme): %d/%d = %.1f%%%n",
                        falseRejects, goodAnswers, 100.0 * falseRejects / goodAnswers);
            }
        }
        System.out.println("Per-message CSV written to: " + csvPath.toAbsolutePath());
        System.out.println("NOTE: main-LLM $ pricing above is a placeholder — verify current pricing before reporting it as final.");
        System.out.println("NOTE: doğruluk rakamları src/test/resources/validator-ab-harness-ground-truth.csv'deki manuel etikete göre.");
        System.out.println("=============================================================================");
    }

    private String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    private String csvEscape(String s) {
        String escaped = (s == null ? "" : s).replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
