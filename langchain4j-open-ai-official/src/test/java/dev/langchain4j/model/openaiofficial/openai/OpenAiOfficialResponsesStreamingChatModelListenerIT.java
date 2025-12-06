package dev.langchain4j.model.openaiofficial.openai;

import static java.util.Collections.singletonList;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelListenerIT extends AbstractStreamingChatModelListenerIT {

    @Override
    protected StreamingChatModel createModel(ChatModelListener listener) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(modelName())
                .temperature(temperature())
                .topP(topP())
                .maxOutputTokens((long) maxTokens())
                .executorService(Executors.newCachedThreadPool())
                .listeners(singletonList(listener))
                .build();
    }

    @Override
    protected String modelName() {
        return ChatModel.GPT_4O_MINI.toString();
    }

    @Override
    protected StreamingChatModel createFailingModel(ChatModelListener listener) {
        var client = OpenAIOkHttpClient.builder().apiKey("banana").build();

        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(modelName())
                .executorService(Executors.newCachedThreadPool())
                .listeners(singletonList(listener))
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
