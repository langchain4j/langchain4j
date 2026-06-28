package dev.langchain4j.model.responsibleai;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;

public class RailTestApp {
    public static void main(String[] args) {
        String apiKey = System.getenv("RAIL_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("ERROR: Please set the RAIL_API_KEY environment variable first.");
            System.err.println("Example: set RAIL_API_KEY=your_api_key");
            System.exit(1);
        }

        ModerationModel model = ResponsibleAiModerationModel.builder()
                .apiKey(apiKey)
                .mode("deep")
                .includeExplanations(true)
                .includeIssues(true)
                .includeSuggestions(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        String textToTest = "To reset your password, open Settings, choose Security, and select Reset password.";
        if (args.length > 0) {
            textToTest = String.join(" ", args);
        }

        System.out.println("--------------------------------------------------");
        System.out.println("Testing text: \"" + textToTest + "\"");
        System.out.println("--------------------------------------------------");

        try {
            Response<Moderation> response = model.moderate(textToTest);
            Moderation moderation = response.content();

            System.out.println("Policy Outcome: " + response.metadata().get("policy_outcome"));
            System.out.println("Flagged: " + moderation.flagged());
            if (moderation.flagged()) {
                System.out.println("Flagged Text: " + moderation.flaggedText());
            }
            System.out.println("Overall RAIL Score: " + response.metadata().get("rail_score.score") + "/10");
            System.out.println("Confidence: " + response.metadata().get("rail_score.confidence"));
            System.out.println("Summary: " + response.metadata().get("rail_score.summary"));
            System.out.println("Credits Consumed: " + response.metadata().get("credits_consumed"));

            System.out.println("\nDimension Scores & Explanations:");
            response.metadata().forEach((key, val) -> {
                if (key.startsWith("dimension_scores.")) {
                    System.out.println("  " + key + " = " + val);
                }
            });
        } catch (Exception e) {
            System.err.println("Error calling RAIL API: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("--------------------------------------------------");
    }
}
