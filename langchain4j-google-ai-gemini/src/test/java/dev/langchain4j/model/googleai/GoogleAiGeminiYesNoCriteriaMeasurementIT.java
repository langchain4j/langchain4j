package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * One-off measurements on Gemini, to be deleted before merge: one-sided yes/no criteria, and whether the model reads
 * question keys.
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiYesNoCriteriaMeasurementIT {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/decisions";
    private static final String MODEL_NAME = "gemini-decision-1";
    private static final List<String> WAYS = List.of("native", "appended", "none");

    private record Case(String id, String input, String question, String side, String criterion, boolean expected) {}

    private static final List<Case> CASES = List.of(
            // yesWhen widens the question: expected yes
            new Case(
                    "Y1",
                    "Can I swap these shoes for a bigger size?",
                    "Is this a return request?",
                    "yes",
                    "The customer wants to send an item back for any reason, including to exchange it.",
                    true),
            new Case(
                    "Y2",
                    "The app logs me out every few minutes, really annoying.",
                    "Is this a bug report?",
                    "yes",
                    "The message describes any unexpected behaviour of the product, even without technical details.",
                    true),
            new Case(
                    "Y3",
                    "My card number is on the back of the letter you sent me.",
                    "Does the message contain personal data?",
                    "yes",
                    "The message mentions any kind of account or card identifier, even without the number itself.",
                    true),
            // yesWhen is not met: expected no
            new Case(
                    "Y4",
                    "How much does shipping to Austria cost?",
                    "Is this a sales lead?",
                    "yes",
                    "The person states a concrete plan to buy a product within the next month.",
                    false),
            new Case(
                    "Y5",
                    "I love the new design!",
                    "Is this feedback actionable?",
                    "yes",
                    "The message names a specific change the team could make.",
                    false),
            new Case(
                    "Y6",
                    "The parcel arrived two days late but everything is fine.",
                    "Should this be escalated?",
                    "yes",
                    "The customer threatens legal action or to cancel their contract.",
                    false),
            // noWhen narrows the question: expected no
            new Case(
                    "N1",
                    "Your product is the worst thing I've ever bought, total garbage.",
                    "Is this message abusive?",
                    "no",
                    "The insults target the product or the company, not a person.",
                    false),
            new Case(
                    "N2",
                    "Please send me the invoice for order 4411 again.",
                    "Is this a billing question?",
                    "no",
                    "The customer only asks for a copy of a document they already received.",
                    false),
            new Case(
                    "N3",
                    "Someone logged into my account from another country last night, but it was me on holiday.",
                    "Is this a security incident?",
                    "no",
                    "The user explains that the activity was their own.",
                    false),
            // noWhen is not met: expected yes
            new Case(
                    "N4",
                    "You idiot, you sent me the wrong item again.",
                    "Is this message abusive?",
                    "no",
                    "The insults target the product or the company, not a person.",
                    true),
            new Case(
                    "N5",
                    "I never got the refund you promised three weeks ago.",
                    "Is this a complaint?",
                    "no",
                    "The customer asks a neutral question without expressing dissatisfaction.",
                    true),
            new Case(
                    "N6",
                    "Someone changed my email address and password, I didn't do this!",
                    "Is this a security incident?",
                    "no",
                    "The user explains that the activity was their own.",
                    true));

    private record KeyProbe(String id, String input, String instructions, String key) {}

    // vague instructions: only the key says what is asked, so the answers differ only if the model reads keys
    private static final List<KeyProbe> VAGUE_KEY_PROBES = List.of(
            new KeyProbe("V1", "I want my money back for the broken blender.", "Does this apply to the message?",
                    "asks_for_refund"),
            new KeyProbe("V2", "Your support team was super helpful, thanks!", "Does this apply to the message?",
                    "is_complaint"),
            new KeyProbe("V3", "Click this link to claim your free iPhone now!!!", "Does this apply to the message?",
                    "is_spam"),
            new KeyProbe("V4", "What time do you open on Saturday?", "Does this apply to the message?",
                    "mentions_a_refund"));

    // clear instructions with a misleading key: the answers differ only if the key distracts the model
    private static final List<KeyProbe> MISLEADING_KEY_PROBES = List.of(
            new KeyProbe("M1", "I want my money back.", "Does the customer ask for a refund?", "is_spam"),
            new KeyProbe("M2", "Free crypto giveaway, send 1 BTC to get 2 back!", "Is this message spam?",
                    "asks_for_refund"),
            new KeyProbe("M3", "The app crashes every time I open the camera.", "Is this a bug report?",
                    "is_praise"),
            new KeyProbe("M4", "Thanks, the new version works great!", "Is this message praise?",
                    "is_bug_report"));

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String apiKey = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void measure_one_sided_yes_no_criteria() throws Exception {
        Map<String, List<Double>> results = new LinkedHashMap<>();
        WAYS.forEach(way -> results.put(way, new ArrayList<>()));
        List<String> errors = new ArrayList<>();
        int totalTokens = 0;

        StringBuilder header = new StringBuilder("case  expected  ");
        for (int i = 0; i < WAYS.size(); i++) {
            header.append(i == 0 ? "" : "  ").append(format("%-12s", WAYS.get(i)));
        }
        System.out.println(header);
        for (Case testCase : CASES) {
            List<String> cells = new ArrayList<>();
            for (String way : WAYS) {
                double pYes;
                try {
                    Map<?, ?> payload = decide(testCase.input(), "q", question(testCase, way));
                    pYes = pYes(payload, "q");
                    if (payload.get("usage") instanceof Map<?, ?> usage
                            && usage.get("total_input_tokens") instanceof Number tokens) {
                        totalTokens += tokens.intValue();
                    }
                } catch (Exception e) {
                    System.out.println(format("%s %s failed: %s", testCase.id(), way, e.getMessage()));
                    errors.add(testCase.id() + " " + way + ": " + e.getMessage());
                    results.get(way).add(null);
                    cells.add(format("%-12s", "ERROR"));
                    continue;
                }
                double pCorrect = testCase.expected() ? pYes : 1 - pYes;
                results.get(way).add(pCorrect);
                cells.add(format("%-12s", format("%.3f %s", pYes, pCorrect > 0.5 ? "ok" : "WRONG")));
            }
            System.out.println(
                    format("%-5s %-9s %s", testCase.id(), testCase.expected() ? "yes" : "no", String.join("  ", cells)));
        }

        System.out.println();
        System.out.println("===== summary (paste this back) =====");
        System.out.println(format("model: %s, cases: %d, input tokens: %d", MODEL_NAME, CASES.size(), totalTokens));
        for (String way : WAYS) {
            List<Double> values =
                    results.get(way).stream().filter(value -> value != null).toList();
            long right = values.stream().filter(value -> value > 0.5).count();
            double mean = values.isEmpty()
                    ? Double.NaN
                    : values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
            int wayErrors = results.get(way).size() - values.size();
            System.out.println(format(
                    "%-9s right %2d/%d  mean p(correct) %.3f  errors %d", way, right, values.size(), mean, wayErrors));
        }
        for (String way : WAYS) {
            List<String> perCase = new ArrayList<>();
            List<Double> values = results.get(way);
            for (int i = 0; i < values.size(); i++) {
                Double value = values.get(i);
                perCase.add(CASES.get(i).id() + "=" + (value == null ? "ERR" : format("%.2f", value)));
            }
            System.out.println(format("%-9s per case: %s", way, String.join(" ", perCase)));
        }
        System.out.println("=====================================");

        // only HTTP and setup errors fail the test, never the measured values
        assertThat(errors).isEmpty();
    }

    @Test
    void measure_whether_question_keys_matter() throws Exception {
        List<String> errors = new ArrayList<>();
        System.out.println("probe  key                 p(yes) with key  p(yes) with q0  delta");
        Map<String, List<Double>> deltas = new LinkedHashMap<>();
        deltas.put("vague", new ArrayList<>());
        deltas.put("misleading", new ArrayList<>());
        for (String group : List.of("vague", "misleading")) {
            for (KeyProbe probe : group.equals("vague") ? VAGUE_KEY_PROBES : MISLEADING_KEY_PROBES) {
                try {
                    Map<String, Object> question = new LinkedHashMap<>();
                    question.put("type", "noul");
                    question.put("instructions", probe.instructions());
                    double withKey = pYes(decide(probe.input(), probe.key(), question), probe.key());
                    double withQ0 = pYes(decide(probe.input(), "q0", question), "q0");
                    deltas.get(group).add(withKey - withQ0);
                    System.out.println(format(
                            "%-6s %-19s %-16.3f %-15.3f %+.3f", probe.id(), probe.key(), withKey, withQ0,
                            withKey - withQ0));
                } catch (Exception e) {
                    System.out.println(format("%s failed: %s", probe.id(), e.getMessage()));
                    errors.add(probe.id() + ": " + e.getMessage());
                }
            }
        }

        System.out.println();
        System.out.println("===== question keys summary =====");
        System.out.println("model: " + MODEL_NAME);
        deltas.forEach((group, values) -> System.out.println(format(
                "%-10s probes %d  mean |delta| %.3f  max |delta| %.3f  deltas %s",
                group,
                values.size(),
                values.stream().mapToDouble(Math::abs).average().orElse(Double.NaN),
                values.stream().mapToDouble(Math::abs).max().orElse(Double.NaN),
                values.stream().map(value -> format("%+.2f", value)).toList())));
        System.out.println("=================================");

        // only HTTP and setup errors fail the test, never the measured values
        assertThat(errors).isEmpty();
    }

    private static double pYes(Map<?, ?> payload, String key) {
        return ((Number) ((Map<?, ?>) ((Map<?, ?>) payload.get("answers")).get(key)).get("noul")).doubleValue();
    }

    private static Map<String, Object> question(Case testCase, String way) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "noul");
        switch (way) {
            case "native" -> {
                question.put("instructions", testCase.question());
                String key = testCase.side().equals("yes") ? "true_description" : "false_description";
                question.put("criteria", Map.of(key, testCase.criterion()));
            }
            case "appended" -> {
                String label = testCase.side().equals("yes") ? "Answer yes when: " : "Answer no when: ";
                question.put("instructions", testCase.question() + "\n" + label + testCase.criterion());
            }
            default -> question.put("instructions", testCase.question());
        }
        return question;
    }

    private Map<?, ?> decide(String input, String key, Map<String, Object> question) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL_NAME);
        body.put("input", input);
        body.put("questions", Map.of(key, question));
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Json.toJson(body)))
                .build();
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                Map<?, ?> payload = Json.fromJson(response.body(), Map.class);
                if (payload.get("prompt_feedback") != null) {
                    throw new IllegalStateException("input blocked: " + Json.toJson(payload.get("prompt_feedback")));
                }
                return payload;
            }
            if (List.of(429, 500, 502, 503, 504).contains(status) && attempt < 3) {
                Thread.sleep(2000L * (attempt + 1));
                continue;
            }
            throw new IllegalStateException("HTTP " + status + ": " + response.body());
        }
    }

    private static String format(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }
}
