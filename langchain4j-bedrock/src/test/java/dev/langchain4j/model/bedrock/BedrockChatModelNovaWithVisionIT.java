package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.*;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockChatModelNovaWithVisionIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(AWS_NOVA_LITE, AWS_NOVA_PRO);
    }

    @Override
    protected String customModelName() {
        return "cohere.command-r-v1:0";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        return BedrockChatModel.builder()
                .defaultRequestParameters(parameters)
                // force a working model with stopSequence parameter for @Tests
                .modelId("cohere.command-r-v1:0")
                .build();
    }

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
    @Override
    protected boolean supportsToolChoiceRequired() {
        return false;
    }

    // output format not supported
    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    // output format not supported
    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean assertExceptionType() {
        return false;
    }

    // OVERRIDE BECAUSE OF INCOHERENCY IN STOPSEQUENCE MANAGEMENT (Nova models include stopSequence)
    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(ChatLanguageModel model) {

        // given
        List<String> stopSequences = List.of("Hello", " Hello");
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().stopSequences(stopSequences).build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Hello World'"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Hello");
        assertThat(aiMessage.text()).doesNotContainIgnoringCase("World");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }
}
