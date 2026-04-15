package dev.langchain4j.model.openai.common;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1_NANO;
import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return OpenAiResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return GPT_4_1_NANO.toString();
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return OpenAiResponsesChatModel.builder()
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
