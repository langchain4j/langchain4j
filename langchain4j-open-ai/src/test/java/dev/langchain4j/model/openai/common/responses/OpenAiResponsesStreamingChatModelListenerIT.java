package dev.langchain4j.model.openai.common.responses;

import static java.util.Collections.singletonList;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName())
                .reasoningEffort("none") // gpt-5.6-luna accepts a temperature only without reasoning
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return "gpt-5.6-luna";
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return OpenAiResponsesStreamingChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .listeners(listener)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return AuthenticationException.class;
    }

    @Override
    protected Integer maxTokens() {
        return 16;
    }
}
