package dev.langchain4j.model.openaiofficial.openai;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.ChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialChatModelListenerIT extends ChatModelListenerIT {

    @Override
    protected ChatLanguageModel createModel(ChatModelListener listener) {
        return OpenAiOfficialChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxCompletionTokens(maxTokens())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return ChatModel.GPT_4O_MINI.toString();
    }

    @Override
    protected ChatLanguageModel createFailingModel(ChatModelListener listener) {
        return OpenAiOfficialChatModel.builder()
                .apiKey("banana")
                .modelName(modelName())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return com.openai.errors.UnauthorizedException.class;
    }
}
