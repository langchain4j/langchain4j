package dev.langchain4j.model.responsibleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class ResponsibleAiModerationModelIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "RAIL_API_KEY", matches = ".+")
    void testRealEvaluation() {
        String apiKey = System.getenv("RAIL_API_KEY");
        ModerationModel model = ResponsibleAiModerationModel.builder()
                .apiKey(apiKey)
                .mode("basic")
                .logRequests(true)
                .logResponses(true)
                .build();

        String safeText =
                "To reset your password, open Settings, choose Security, and select Reset password. We will email you a secure link.";
        Response<Moderation> response = model.moderate(safeText);

        assertThat(response.content().flagged()).isFalse();
        assertThat(response.metadata()).containsKey("rail_score.score");

        Double score = (Double) response.metadata().get("rail_score.score");
        System.out.println("Overall RAIL Score: " + score);
    }
}
