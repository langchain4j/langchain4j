package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DynamicToolProviderMemoryEvictionTest {

    interface Assistant {
        String chat(String userMessage);
    }

    interface StreamingAssistant {
        TokenStream chat(String userMessage);
    }

    private static final ToolProvider ALWAYS_TIME_PROVIDER = new ToolProvider() {

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            return ToolProviderResult.builder()
                    .add(
                            ToolSpecification.builder()
                                    .name("get_time")
                                    .description("Returns the current time")
                                    .build(),
                            (req, memoryId) -> "12:00")
                    .build();
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    };

    private static AiMessage callTime() {
        return AiMessage.from(
                ToolExecutionRequest.builder().name("get_time").arguments("{}").build());
    }

    @Test
    void blocking_tool_loop_survives_user_message_eviction() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(callTime(), callTime(), AiMessage.from("It is 12:00."));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(2))
                .toolProvider(ALWAYS_TIME_PROVIDER)
                .build();

        assertThat(assistant.chat("What time is it?")).contains("12:00");
    }

    @Test
    void streaming_tool_loop_survives_user_message_eviction() throws Exception {

        StreamingChatModelMock model =
                StreamingChatModelMock.thatAlwaysStreams(callTime(), callTime(), AiMessage.from("It is 12:00."));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(2))
                .toolProvider(ALWAYS_TIME_PROVIDER)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("What time is it?")
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        assertThat(future.get(60, TimeUnit.SECONDS).aiMessage().text()).contains("12:00");
    }
}
