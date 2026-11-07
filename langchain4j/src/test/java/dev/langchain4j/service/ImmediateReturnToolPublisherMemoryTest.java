package dev.langchain4j.service;

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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class ImmediateReturnToolPublisherMemoryTest {

    static class Booking {

        @Tool(returnBehavior = ReturnBehavior.IMMEDIATE)
        String book(String what) {
            return "Booking#42";
        }
    }

    interface ReactiveAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    @Test
    void should_store_result_of_immediate_return_tool_in_memory() throws Exception {

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(
                        StreamingEventChatModelMock.thatStreams(AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("book")
                                .arguments("{\"arg0\": \"hotel\"}")
                                .build())))
                .chatMemory(memory)
                .tools(new Booking())
                .build();

        CompletableFuture<Void> completed = new CompletableFuture<>();
        assistant.chat("Book a hotel").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });
        completed.get(10, SECONDS);

        List<ChatMessage> messages = memory.messages();
        assertThat(messages).hasSize(3);
        assertThat(((AiMessage) messages.get(1)).hasToolExecutionRequests()).isTrue();
        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) messages.get(2)).text()).isEqualTo("Booking#42");
    }
}
