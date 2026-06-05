package dev.langchain4j.model.watsonx.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Response;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Think;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
public class WatsonxChatModelThinkingIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_GRANITE_3_3_DEPLOYMENT_ID");

    @Test
    public void should_return_and_send_thinking() {

        ChatModel chatModel = WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(DEPLOYMENT_ID)
                .thinking(
                        ExtractionTags.of(new Think("<think>", "</think>"), new Response("<response>", "</response>")))
                .maxOutputTokens(0)
                .timeout(Duration.ofSeconds(30))
                .build();

        var chatResponse = chatModel.chat(UserMessage.from("Why the sky is blue?"));
        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.thinking()).doesNotContain("<think>", "</think>");
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.text()).doesNotContain("<response>", "</response>");
    }

    @Test
    void should_return_and_NOT_send_thinking() {

        ChatModel chatModel = WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(DEPLOYMENT_ID)
                .build();

        var chatResponse = chatModel.chat(UserMessage.from("Why the sky is blue?"));
        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isBlank();
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    public void should_return_thinking_using_gpt_oss() {
        var chatModel = WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName("openai/gpt-oss-120b")
                .timeout(Duration.ofSeconds(30))
                .thinking(ThinkingEffort.MEDIUM)
                .build();

        var aiMessage = chatModel.chat(UserMessage.from("Hello!")).aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.thinking()).isNotBlank();
    }
}
