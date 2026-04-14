package dev.langchain4j.service.common.openai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Set;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesAiServicesWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                new StreamingChatModelAdapter(
                        OpenAiResponsesStreamingChatModel.builder()
                                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                                .apiKey(System.getenv("OPENAI_API_KEY"))
                                .modelName("gpt-5.4-mini")
                                .temperature(0.0)
                                .strictJsonSchema(true)
                                .logRequests(true)
                                .logResponses(true)
                                .build(),
                        true),
                new StreamingChatModelAdapter(
                        OpenAiResponsesStreamingChatModel.builder()
                                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                                .apiKey(System.getenv("OPENAI_API_KEY"))
                                .modelName("gpt-5.4-mini")
                                .temperature(0.0)
                                .strictJsonSchema(false)
                                .logRequests(true)
                                .logResponses(true)
                                .build(),
                        false)
        );
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model instanceof StreamingChatModelAdapter adapter && adapter.strictJsonSchemaEnabled;
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
