package dev.langchain4j.service.tool;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ImmediateReturnToolMemoryTest {

    static class Booking {

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        String book(String what) {
            return "Booking#42";
        }
    }

    interface SyncAssistant {

        Result<String> chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<Result<String>> chat(String message);
    }

    private static ChatModelMock bookingModel() {
        return new ChatModelMock(request -> AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("book")
                .arguments("{\"arg0\": \"hotel\"}")
                .build()));
    }

    private static void assertToolResultIsInMemory(ChatMemory memory) {
        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) messages.get(1)).hasToolExecutionRequests()).isTrue();
        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) messages.get(2)).text()).isEqualTo("Booking#42");
    }

    @Test
    void sync_service_should_store_result_of_immediate_return_tool_in_memory() {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(bookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        Result<String> result = assistant.chat("Book a hotel");

        assertThat(result.toolExecutions()).hasSize(1);
        assertToolResultIsInMemory(memory);
    }

    @Test
    void async_service_should_store_result_of_immediate_return_tool_in_memory() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(bookingModel())
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        Result<String> result = assistant.chat("Book a hotel").get(10, SECONDS);

        assertThat(result.toolExecutions()).hasSize(1);
        assertToolResultIsInMemory(memory);
    }
}
