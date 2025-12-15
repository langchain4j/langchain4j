package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.watsonx.ai.detection.detector.Hap;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.watsonx.WatsonxModerationModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxModerationModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final ModerationModel model = WatsonxModerationModel.builder()
            .baseUrl(URL)
            .apiKey(API_KEY)
            .projectId(PROJECT_ID)
            .detectors(Hap.ofDefaults())
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    public void should_flag_content_and_include_metadata_from_detection() {

        var response = model.moderate("I kill you!");
        assertTrue(response.content().flagged());
        assertEquals("I kill you!", response.content().flaggedText());

        var metadata = response.metadata();
        assertEquals(0, metadata.get("start"));
        assertEquals(11, metadata.get("end"));
        assertEquals("hap", metadata.get("detection_type"));
        assertEquals("has_HAP", metadata.get("detection"));
        assertEquals(0.98, (float) metadata.get("score"), 0.01);
    }

    @Test
    public void should_return_not_flagged() {

        var response = model.moderate(List.of(
                SystemMessage.from("systemMessage"),
                UserMessage.from("userMessage"),
                AiMessage.from("aiMessage"),
                ToolExecutionResultMessage.from("id", "toolName", "toolExecutionResult")));

        assertFalse(response.content().flagged());
        assertNull(response.content().flaggedText());
    }

    @Test
    void should_return_the_flagged_response() {

        var response = model.moderate(List.of(
                SystemMessage.from("systemMessage"),
                UserMessage.from("userMessage"),
                AiMessage.from("I kill you!"),
                ToolExecutionResultMessage.from("id", "toolName", "toolExecutionResult")));

        assertTrue(response.content().flagged());
        assertEquals("I kill you!", response.content().flaggedText());

        var metadata = response.metadata();
        assertEquals(0, metadata.get("start"));
        assertEquals(11, metadata.get("end"));
        assertEquals("hap", metadata.get("detection_type"));
        assertEquals("has_HAP", metadata.get("detection"));
        assertEquals(0.98, (float) metadata.get("score"), 0.01);
    }
}
