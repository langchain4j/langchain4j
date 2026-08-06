package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatModel;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatRequestParameters;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY_GATEWAY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_MODEL", matches = ".+")
public class WatsonxGatewayChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY_GATEWAY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_MODEL");

    @Test
    void should_do_sync_chat() {
        var chatModel = WatsonxGatewayChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(30))
                .build();

        var answer = chatModel.chat("What is the capital of Italy? Answer in one word.");
        assertNotNull(answer);
        assertTrue(answer.toLowerCase().contains("rome"));
    }

    @Test
    void should_override_builder_defaults_with_per_request_parameters() {
        var chatModel = WatsonxGatewayChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .modelName(MODEL)
                .maxOutputTokens(200)
                .timeout(Duration.ofSeconds(30))
                .build();

        var chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say hi."))
                .parameters(WatsonxGatewayChatRequestParameters.builder()
                        .maxOutputTokens(5)
                        .build())
                .build();

        var response = chatModel.chat(chatRequest);
        assertNotNull(response.aiMessage().text());
        assertNotNull(response.modelName());
        assertTrue(response.tokenUsage().outputTokenCount() <= 5);
    }
}
