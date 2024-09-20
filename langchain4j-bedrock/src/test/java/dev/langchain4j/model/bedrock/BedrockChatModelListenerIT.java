package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;
import java.util.Collections;

public class BedrockChatModelListenerIT extends ChatModelListenerIT {
    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(300)
                .region(Region.US_EAST_1)
                .model(BedrockAnthropicMessageChatModel.Types.AnthropicClaude3SonnetV1.getValue())
                .maxRetries(1)
                .timeout(Duration.ofMinutes(2L))
                .listeners(Collections.singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "anthropic.claude-3-sonnet-20240229-v1:0";
    }

    @Override
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
        return BedrockAnthropicMessageChatModel
                .builder()
                .temperature(0.50f)
                .maxTokens(1)
                .region(Region.US_EAST_1)
                .model("banana")
                .maxRetries(1)
                .listeners(Collections.singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return RuntimeException.class;
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
}
