package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.openai.InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatModel> models() {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .executorService(Executors.newCachedThreadPool())
                .build();

        return List.of(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        OpenAiOfficialResponsesStreamingChatModel.Builder modelBuilder =
                OpenAiOfficialResponsesStreamingChatModel.builder()
                        .client(client)
                        .executorService(Executors.newCachedThreadPool());

        if (parameters.modelName() != null) {
            modelBuilder.modelName(parameters.modelName());
        } else {
            modelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE.toString());
        }

        if (parameters instanceof OpenAiOfficialChatRequestParameters openAiParams) {
            if (openAiParams.temperature() != null) {
                modelBuilder.temperature(openAiParams.temperature());
            }
            if (openAiParams.topP() != null) {
                modelBuilder.topP(openAiParams.topP());
            }
            if (openAiParams.maxOutputTokens() != null) {
                modelBuilder.maxOutputTokens(openAiParams.maxOutputTokens().longValue());
            }
        }

        return modelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialTokenUsage.class;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return null; // TODO implement
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "{\""));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "city"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "\":\""));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "Mun"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "ich"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "\"}"));
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "{\"ci"));
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "ty\": "));
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "\"Munic"));
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "h\"}"));
        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\"city\": \"Munich\"}"));

        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "{\"co"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "untry"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "\": \"Fr"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "ance"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "\"}"));
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\": \"France\"}"));
    }

    @Test
    void should_work_with_o_models() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("o4-mini")
                .executorService(Executors.newCachedThreadPool())
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Berlin");
    }
}
