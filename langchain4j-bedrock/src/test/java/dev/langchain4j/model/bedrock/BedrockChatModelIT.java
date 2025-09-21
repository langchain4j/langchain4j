package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.regions.Region;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelIT {

    @Test
    void should_generate_with_default_config() {

        BedrockChatModel bedrockChatModel = new BedrockChatModel("us.amazon.nova-micro-v1:0");
        assertThat(bedrockChatModel).isNotNull();

        ChatResponse response = bedrockChatModel.chat(UserMessage.from("hi, how are you doing?"));

        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isIn(FinishReason.STOP, FinishReason.LENGTH);
    }

    @Test
    void should_accept_PDF_documents() {

        // given
        ChatModel model =
                BedrockChatModel.builder().modelId("us.amazon.nova-lite-v1:0").build();
        UserMessage msg = UserMessage.from(
                PdfFileContent.from(
                        Paths.get("src/test/resources/gemini-doc-snapshot.pdf").toUri()),
                TextContent.from("Provide a summary of the document"));

        // when
        ChatResponse response = model.chat(List.of(msg));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Gemini");
    }

    @Test
    void should_fail_if_reasoning_is_enabled_on_non_reasoning_model() {

        // given
        String modelNotSupportingReasoning = "us.amazon.nova-lite-v1:0";

        ChatModel model = BedrockChatModel.builder()
                .modelId(modelNotSupportingReasoning)
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .enableReasoning(1024)
                        .build())
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when-then
        assertThatThrownBy(() -> model.chat(userMessage))
                .isExactlyInstanceOf(dev.langchain4j.exception.InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) {

        // given
        Duration timeout = Duration.ofMillis(millis);

        ChatModel model = BedrockChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .maxRetries(0)
                .timeout(timeout)
                .build();

        // when
        assertThatThrownBy(() -> model.chat("hi"))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @Test
    void should_support_gpt_oss_model() {

        BedrockChatModel bedrockChatModel = BedrockChatModel.builder()
                .modelId("openai.gpt-oss-20b-1:0")
                .region(Region.US_WEST_2)
                .build();

        String answer = bedrockChatModel.chat("What is the capital of Germany?");

        assertThat(answer).contains("Berlin");
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
