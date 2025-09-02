package dev.langchain4j.model.watsonx.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxChatModelThinkingIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @Test
    public void should_return_and_send_thinking() {

        ChatModel chatModel = createChatModel("ibm/granite-3-3-8b-instruct").build();
        var chatResponse = chatModel.chat(UserMessage.from("Why the sky is blue?"));
        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    void should_return_and_NOT_send_thinking() {

        ChatModel chatModel = WatsonxChatModel.builder()
                .url(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName("ibm/granite-3-3-8b-instruct")
                .logRequests(true)
                .logResponses(true)
                .build();

        var chatResponse = chatModel.chat(UserMessage.from("Why the sky is blue?"));
        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isBlank();
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    void should_not_send_the_thinking_request() {

        ChatModel chatModel = createChatModel("ibm/granite-3-3-8b-instruct").build();

        assertThrows(
                InvalidRequestException.class,
                () -> chatModel.chat(
                        SystemMessage.from("You are an helpful assistant"), UserMessage.from("Why the sky is blue?")),
                "The thinking/reasoning cannot be activated when a system message is present");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Why the sky is blue?"))
                .toolSpecifications(ToolSpecification.builder().name("sum").build())
                .build();

        assertThrows(
                InvalidRequestException.class,
                () -> chatModel.chat(chatRequest),
                "The thinking/reasoning cannot be activated when tools are used");
    }

    private WatsonxChatModel.Builder createChatModel(String model) {
        return WatsonxChatModel.builder()
                .url(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(model)
                .thinking(ExtractionTags.of("think", "response"))
                .logRequests(true)
                .logResponses(true)
                .maxOutputTokens(0)
                .timeLimit(Duration.ofSeconds(30));
    }
}
