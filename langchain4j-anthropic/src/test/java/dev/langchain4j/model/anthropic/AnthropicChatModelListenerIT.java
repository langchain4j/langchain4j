package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.anthropic.internal.client.AnthropicHttpException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import java.time.Duration;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_SONNET_20240229;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class AnthropicChatModelListenerIT extends ChatModelListenerIT {
    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return AnthropicChatModel.builder()
                .baseUrl("https://api.anthropic.com/v1/")
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .version("2023-06-01")
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .topK(1)
                .maxTokens(maxTokens())
                .stopSequences(asList("hello", "world"))
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return CLAUDE_3_SONNET_20240229.toString();
    }

    @Override
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
        return AnthropicChatModel.builder()
                .apiKey("err")
                .topP(topP())
                .topK(1)
                .maxTokens(maxTokens())
                .modelName("test")
                .stopSequences(asList("hello", "world"))
                .timeout(Duration.ofSeconds(30))
                .maxRetries(1)
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
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
