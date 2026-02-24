package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001;
import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName())
                .temperature(temperature())
                .maxTokens(maxTokens())
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return CLAUDE_HAIKU_4_5_20251001.toString();
    }

    @Override
    protected Double topP() {
        return null;
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return AnthropicStreamingChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .logRequests(true)
                .logResponses(true)
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return dev.langchain4j.exception.AuthenticationException.class;
    }
}
