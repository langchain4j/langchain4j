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
 * One-off measurement on Gemini, to be deleted before merge: one-sided yes/no criteria, round 2.
 * A one-sided criterion is a sufficient condition: when it does not hold, the question itself decides.
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiYesNoCriteriaMeasurementIT {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/decisions";
    private static final String MODEL_NAME = "gemini-decision-1";
    private static final List<String> WAYS = List.of("native", "none", "native+hint", "also", "native+fill");

    /**
     * @param expected the right answer, or null when the right answer is unclear (not scored)
     */
    private record Case(String id, String input, String question, String side, String criterion, Boolean expected) {}

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
            new Case(
                    "Y12",
                    "I've been charged twice for the same subscription this month.",
                    "Is this a refund request?",
                    "yes",
                    "The customer reports being charged more than they should, even without asking for money back.",
                    true),
            // yesWhen is not met and the question itself is unclear: not scored (round 1 expected no)
            new Case(
                    "Y4",
                    "How much does shipping to Austria cost?",
                    "Is this a sales lead?",
                    "yes",
                    "The person states a concrete plan to buy a product within the next month.",
                    null),
            // yesWhen is not met and the question itself says no: expected no
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
            // yesWhen is not met, but the question itself says yes: expected yes
            new Case(
                    "Y7",
                    "The export produces a corrupted PDF.",
                    "Is this a bug report?",
                    "yes",
                    "The app crashes.",
                    true),
            new Case(
                    "Y8",
                    "I'd like to return this jacket, I don't like the color.",
                    "Is this a return request?",
                    "yes",
                    "The customer wants to exchange an item for another size.",
                    true),
            new Case(
                    "Y9",
                    "Please call me back on +49 170 1234567, my name is Anna Berger.",
                    "Does the message contain personal data?",
                    "yes",
                    "The message contains an email address.",
                    true),
            new Case(
                    "Y10",
                    "My order hasn't arrived after two weeks and nobody replies to my emails, this is unacceptable.",
                    "Is this a complaint?",
                    "yes",
                    "The customer mentions a damaged product.",
                    true),
            new Case(
                    "Y11",
                    "What does the premium plan cost for 50 users?",
                    "Is this a sales question?",
                    "yes",
                    "The person asks for a discount.",
                    true),
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
            // noWhen is not met and the question itself says yes: expected yes
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
                    true),
            // noWhen is not met, but the question itself says no: expected no
            new Case(
                    "N7",
                    "What are your opening hours?",
                    "Is this message abusive?",
                    "no",
                    "The insults target the product, not a person.",
                    false),
            new Case(
                    "N8",
                    "Can you recommend a good hiking trail near Munich?",
                    "Is this a billing question?",
                    "no",
                    "The customer only asks for a copy of an invoice.",
                    false),
            new Case(
                    "N9",
                    "Thanks for the quick delivery, everything works great!",
                    "Is this a complaint?",
                    "no",
                    "The customer asks a neutral question.",
                    false));

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String apiKey = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void measure_one_sided_yes_no_criteria() throws Exception {
        Map<String, List<Double>> pYesByWay = new LinkedHashMap<>();
        WAYS.forEach(way -> pYesByWay.put(way, new ArrayList<>()));
        List<String> errors = new ArrayList<>();
        int totalTokens = 0;

        StringBuilder header = new StringBuilder("case  expected  ");
        for (String way : WAYS) {
            header.append(format("%-13s", way));
        }
        System.out.println(header);
        for (Case testCase : CASES) {
            StringBuilder row = new StringBuilder(format("%-5s %-9s ", testCase.id(), expected(testCase)));
            for (String way : WAYS) {
                try {
                    Map<?, ?> payload = decide(testCase.input(), "q", question(testCase, way));
                    double pYes = pYes(payload, "q");
                    totalTokens += inputTokens(payload);
                    pYesByWay.get(way).add(pYes);
                    String verdict = testCase.expected() == null ? "" : isRight(testCase, pYes) ? "ok" : "WRONG";
                    row.append(format("%-13s", format("%.3f %s", pYes, verdict)));
                } catch (Exception e) {
                    System.out.println(format("%s %s failed: %s", testCase.id(), way, e.getMessage()));
                    errors.add(testCase.id() + " " + way + ": " + e.getMessage());
                    pYesByWay.get(way).add(null);
                    row.append(format("%-13s", "ERROR"));
                }
            }
            System.out.println(row);
        }

        long scored = CASES.stream().filter(testCase -> testCase.expected() != null).count();
        System.out.println();
        System.out.println("===== summary round 2 (paste this back) =====");
        System.out.println(format(
                "model: %s, cases: %d (%d scored), input tokens: %d",
                MODEL_NAME, CASES.size(), scored, totalTokens));
        for (String way : WAYS) {
            int right = 0;
            double sum = 0;
            int answered = 0;
            List<String> wrong = new ArrayList<>();
            for (int i = 0; i < CASES.size(); i++) {
                Case testCase = CASES.get(i);
                Double pYes = pYesByWay.get(way).get(i);
                if (testCase.expected() == null || pYes == null) {
                    continue;
                }
                answered++;
                sum += testCase.expected() ? pYes : 1 - pYes;
                if (isRight(testCase, pYes)) {
                    right++;
                } else {
                    wrong.add(testCase.id());
                }
            }
            System.out.println(format(
                    "%-12s right %2d/%d  mean p(correct) %.3f  wrong %s",
                    way, right, answered, answered == 0 ? Double.NaN : sum / answered, wrong));
        }
        for (String way : WAYS) {
            List<String> perCase = new ArrayList<>();
            for (int i = 0; i < CASES.size(); i++) {
                Double pYes = pYesByWay.get(way).get(i);
                perCase.add(CASES.get(i).id() + "=" + (pYes == null ? "ERR" : format("%.2f", pYes)));
            }
            System.out.println(format("%-12s p(yes): %s", way, String.join(" ", perCase)));
        }
        System.out.println("=============================================");

        // only HTTP and setup errors fail the test, never the measured values
        assertThat(errors).isEmpty();
    }

    private static String expected(Case testCase) {
        return testCase.expected() == null ? "unclear" : testCase.expected() ? "yes" : "no";
    }

    private static boolean isRight(Case testCase, double pYes) {
        return testCase.expected() == (pYes > 0.5);
    }

    private static int inputTokens(Map<?, ?> payload) {
        return payload.get("usage") instanceof Map<?, ?> usage
                        && usage.get("total_input_tokens") instanceof Number tokens
                ? tokens.intValue()
                : 0;
    }

    private static double pYes(Map<?, ?> payload, String key) {
        return ((Number) ((Map<?, ?>) ((Map<?, ?>) payload.get("answers")).get(key)).get("noul")).doubleValue();
    }

    private static Map<String, Object> question(Case testCase, String way) {
        boolean yesSide = testCase.side().equals("yes");
        String side = yesSide ? "yes" : "no";
        String criterionKey = yesSide ? "true_description" : "false_description";
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "noul");
        switch (way) {
            case "native" -> {
                question.put("instructions", testCase.question());
                question.put("criteria", Map.of(criterionKey, testCase.criterion()));
            }
            case "native+hint" -> {
                question.put(
                        "instructions",
                        testCase.question() + "\nThe criterion for " + side + " is one reason to answer " + side
                                + ", not the only one. Otherwise, answer the question as asked.");
                question.put("criteria", Map.of(criterionKey, testCase.criterion()));
            }
            case "also" -> question.put(
                    "instructions", testCase.question() + "\nAlso answer " + side + " when: " + testCase.criterion());
            case "native+fill" -> {
                question.put("instructions", testCase.question());
                Map<String, String> criteria = new LinkedHashMap<>();
                if (yesSide) {
                    criteria.put("true_description", testCase.criterion());
                    criteria.put("false_description", "The answer to the question itself is no.");
                } else {
                    criteria.put("true_description", "The answer to the question itself is yes.");
                    criteria.put("false_description", testCase.criterion());
                }
                question.put("criteria", criteria);
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
