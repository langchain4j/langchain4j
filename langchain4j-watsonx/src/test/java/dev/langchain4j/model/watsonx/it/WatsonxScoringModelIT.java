package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.watsonx.ai.rerank.RerankParameters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.watsonx.WatsonxScoringModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_RERANK_MODEL", matches = ".+")
public class WatsonxScoringModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_RERANK_MODEL");

    static final WatsonxScoringModel model = WatsonxScoringModel.builder()
            .baseUrl(URL)
            .apiKey(API_KEY)
            .projectId(PROJECT_ID)
            .modelName(MODEL)
            .build();

    @Test
    void should_score_single_text() {

        var response =
                model.score("Water boils at 100 degrees Celsius at sea level.", "At what temperature does water boil?");

        assertTrue(response.content() > 0.0);
        assertTrue(response.tokenUsage().inputTokenCount() > 0);
        assertNull(response.finishReason());
    }

    @Test
    void should_score_all_segments_in_the_order_of_the_input() {

        var query = "Which planet is the closest to the Sun?";
        var segments = List.of(
                TextSegment.from("Bees pollinate flowers while collecting nectar."),
                TextSegment.from("Mercury is the planet closest to the Sun."),
                TextSegment.from("The violin has four strings tuned in fifths."));

        var response = model.scoreAll(segments, query);
        var scores = response.content();

        assertEquals(3, scores.size());
        assertTrue(scores.get(1) > scores.get(0));
        assertTrue(scores.get(1) > scores.get(2));
        assertTrue(response.tokenUsage().inputTokenCount() > 0);
        assertNull(response.finishReason());
    }

    @Test
    void should_score_all_segments_with_rerank_parameters() {

        var query = "Which planet is the closest to the Sun?";
        var segments = List.of(
                TextSegment.from("Mercury is the planet closest to the Sun, orbiting it every 88 Earth days."),
                TextSegment.from("Neptune is the farthest planet from the Sun, orbiting it every 165 Earth years."));

        var response = model.scoreAll(segments, query);
        var truncatedResponse = model.scoreAll(
                segments,
                query,
                RerankParameters.builder().truncateInputTokens(4).build());

        assertEquals(2, truncatedResponse.content().size());
        assertTrue(truncatedResponse.tokenUsage().inputTokenCount()
                < response.tokenUsage().inputTokenCount());
    }
}
