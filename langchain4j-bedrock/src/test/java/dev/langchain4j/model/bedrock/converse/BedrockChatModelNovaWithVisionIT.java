package dev.langchain4j.model.bedrock.converse;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.model.bedrock.converse.BedrockChatModel.dblToFloat;
import static dev.langchain4j.model.bedrock.converse.TestedModels.*;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

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
        // TODO
        return BedrockChatModel.builder()
                //force a working model with stopSequence parameter for @Tests
                .modelId("cohere.command-r-v1:0")
                .stopSequences(parameters.stopSequences())
                .temperature(dblToFloat(parameters.temperature()))
                .topP(dblToFloat(parameters.topP()))
                .maxTokens(parameters.maxOutputTokens())
                .build();
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    //OVERRIDE BECAUSE OF INCOHERENCY IN STOPSEQUENCE MANAGEMENT (Nova models include stopSequence)
    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(ChatLanguageModel model) {

        // given
        List<String> stopSequences = List.of("World", " World");
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .stopSequences(stopSequences)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Hello World'"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Hello");
        assertThat(aiMessage.text()).containsIgnoringCase("World");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

}
