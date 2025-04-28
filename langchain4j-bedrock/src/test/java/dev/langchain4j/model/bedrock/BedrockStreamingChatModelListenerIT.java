package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CompletionException;

import static dev.langchain4j.model.bedrock.BedrockAnthropicStreamingChatModel.Types.AnthropicClaudeV2_1;
import static dev.langchain4j.model.bedrock.BedrockChatModelWithInvokeAPIIT.sleepIfNeeded;
import static java.util.Collections.singletonList;

class BedrockStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return BedrockAnthropicStreamingChatModel.builder()
                .model(modelName())
                .temperature(temperature())
                .topP(topP().floatValue())
                .maxTokens(maxTokens())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return AnthropicClaudeV2_1.getValue();
    }

    @Override
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertResponseModel() {
        return false;
    }

    @Override
    protected boolean assertTokenUsage() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return BedrockAnthropicStreamingChatModel.builder()
                .model("banana")
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return CompletionException.class;
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
