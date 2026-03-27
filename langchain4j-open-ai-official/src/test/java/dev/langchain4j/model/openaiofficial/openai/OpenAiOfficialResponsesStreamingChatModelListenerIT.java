package dev.langchain4j.model.openaiofficial.openai;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        return InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens(maxTokens())
                .listeners(listener)
                .build();
    }

    @Override
    protected String modelName() {
        return ChatModel.GPT_4O_MINI.toString();
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        return InternalOpenAiOfficialTestHelper.responsesStreamingChatModelBuilder()
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
