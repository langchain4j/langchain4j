package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import software.amazon.awssdk.regions.Region;

import java.util.Collections;
import java.util.concurrent.CompletionException;

public class BedrockStreamingChatModelListenerIT extends StreamingChatModelListenerIT {
    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return BedrockAnthropicStreamingChatModel
                .builder()
                .temperature(0.5)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .maxRetries(1)
                .listeners(Collections.singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "anthropic.claude-v2";
    }

    @Override
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return BedrockAnthropicStreamingChatModel
                .builder()
                .model("arw")
                .maxRetries(1)
                .listeners(Collections.singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return CompletionException.class;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }

    @Override
    protected Double temperature() {
        return 0.5;
    }

    @Override
    protected Double topP() {
        return 0.9990000128746033;
    }

    @Override
    protected Integer maxTokens() {
        return 300;
    }

    @Override
    protected boolean assertResponseModel() {
        return false;
    }

    @Override
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected boolean supportsTokenUsage() {
        return false;
    }
}
