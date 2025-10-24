package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.azureopenai.InternalAzureOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

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
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import java.util.List;

import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatModel> models() {
        return InternalOpenAiOfficialTestHelper.chatModelsStreamingNormalAndJsonStrict();
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialStreamingChatModel.Builder openAiChatModelBuilder = OpenAiOfficialStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultRequestParameters(parameters);

        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE);
        }
        return openAiChatModelBuilder.build();
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
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "{\"")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "city")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "\":\"")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "Mun")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "ich")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "{\"ci")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "ty\": ")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "\"Munic")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "h\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\"city\": \"Munich\"}"));

        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "{\"co")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "untry")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "\": \"Fr")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "ance")), any());
        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\": \"France\"}"));
    }

    @Test
    void should_work_with_o_models() {

        // given

        StreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("o4-mini")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Berlin");
    }
}
