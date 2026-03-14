package dev.langchain4j.model.openaiofficial.openai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesAiServicesWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    private static final ChatModel MODEL = new StreamingChatModelAdapter(
            OpenAiOfficialResponsesStreamingChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                    .temperature(0.0)
                    .strict(true)
                    .build(),
            true);

    @Override
    protected List<ChatModel> models() {
        return List.of(MODEL);
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model instanceof StreamingChatModelAdapter adapter && adapter.strictJsonSchemaEnabled;
    }

    @Override
    protected String localDateTimeFieldsSystemMessage() {
        return "Fill all fields when information is present. Never return nulls.";
    }

    private static class StreamingChatModelAdapter implements ChatModel {

        private final StreamingChatModel streamingChatModel;
        private final boolean strictJsonSchemaEnabled;

        private StreamingChatModelAdapter(StreamingChatModel streamingChatModel, boolean strictJsonSchemaEnabled) {
            this.streamingChatModel = streamingChatModel;
            this.strictJsonSchemaEnabled = strictJsonSchemaEnabled;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            streamingChatModel.chat(chatRequest, handler);
            return handler.get();
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return streamingChatModel.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return streamingChatModel.listeners();
        }

        @Override
        public ModelProvider provider() {
            return streamingChatModel.provider();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return streamingChatModel.supportedCapabilities();
        }
    }
}
