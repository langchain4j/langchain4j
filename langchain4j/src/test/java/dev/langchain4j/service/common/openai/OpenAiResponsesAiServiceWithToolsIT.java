package dev.langchain4j.service.common.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

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
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(new StreamingChatModelAdapter(OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_4_O_MINI.toString())
                .temperature(0.0)
                .maxToolCalls(2)
                .parallelToolCalls(true)
                .strict(true)
                .build()));
    }

    @Override
    protected boolean supportsMapParameters() {
        return false;
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean hasDeterministicParallelToolExecutionAssertions() {
        return false;
    }

    private static class StreamingChatModelAdapter implements ChatModel {

        private final StreamingChatModel streamingChatModel;

        private StreamingChatModelAdapter(StreamingChatModel streamingChatModel) {
            this.streamingChatModel = streamingChatModel;
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
