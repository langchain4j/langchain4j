package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.anthropic.internal.client.AnthropicHttpException;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import java.time.Duration;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class AnthropicStreamingChatModelListenerIT extends StreamingChatModelListenerIT {

    @Override
    protected StreamingChatLanguageModel createModel(ChatModelListener listener) {
        return AnthropicStreamingChatModel.builder()
                .baseUrl("https://api.anthropic.com/v1/")
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .version("2023-06-01")
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .topK(1)
                .maxTokens(maxTokens())
                .stopSequences(asList("hello", "world"))
                .timeout(Duration.ofSeconds(3))
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return AnthropicChatModelName.CLAUDE_3_SONNET_20240229.toString();
    }

    @Override
    protected StreamingChatLanguageModel createFailingModel(ChatModelListener listener) {
        return AnthropicStreamingChatModel.builder()
                .apiKey("err")
                .timeout(Duration.ofSeconds(3))
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return AnthropicHttpException.class;
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
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
