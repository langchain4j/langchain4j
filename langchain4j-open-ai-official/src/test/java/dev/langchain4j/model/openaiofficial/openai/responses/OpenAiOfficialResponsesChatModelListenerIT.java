package dev.langchain4j.model.openaiofficial.openai.responses;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatModel;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesChatModelListenerIT extends AbstractChatModelListenerIT {

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .listeners(listener)
                .build();
    }

    @Override
    protected String modelName() {
        return "gpt-4o-mini";
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return OpenAiOfficialResponsesChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .listeners(listener)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return com.openai.errors.UnauthorizedException.class;
    }

    @Override
    protected Integer maxTokens() {
        return 16; // OpenAI Responses API requires minimum of 16
    }
}
