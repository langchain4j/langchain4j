package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.BedrockAnthropicStreamingChatModel.Types.AnthropicClaudeV2_1;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.util.List;
import java.util.concurrent.CompletionException;

class BedrockStreamingChatModelListenerIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return BedrockAnthropicStreamingChatModel.builder()
                .model(modelName())
                .temperature(temperature())
                .topP(topP().floatValue())
                .maxTokens(maxTokens())
                .listeners(List.of(listener))
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
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return CompletionException.class;
    }
}
