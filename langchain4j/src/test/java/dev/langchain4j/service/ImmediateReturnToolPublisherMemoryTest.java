package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    // delegates to a real memory, but holds back the persisting of the tool result until released
    static class SlowToolResultMemory implements ChatMemory {

        final ChatMemory delegate = MessageWindowChatMemory.withMaxMessages(10);
        final CompletableFuture<Void> toolResultPersisted = new CompletableFuture<>();
        final CountDownLatch toolResultPersistRequested = new CountDownLatch(1);

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public CompletableFuture<Void> addAsync(List<ChatMessage> messages) {
            messages.forEach(delegate::add);
            if (messages.stream().anyMatch(ToolExecutionResultMessage.class::isInstance)) {
                toolResultPersistRequested.countDown();
                return toolResultPersisted;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<ChatMessage>> messagesAsync() {
            return CompletableFuture.completedFuture(delegate.messages());
        }
    }

    @Test
    void should_not_run_output_guardrails_when_cancelled_while_the_tool_result_is_persisted() throws Exception {

        SlowToolResultMemory memory = new SlowToolResultMemory();
        AtomicInteger guardrailCalls = new AtomicInteger();
        OutputGuardrail guardrail = new OutputGuardrail() {

            @Override
            public OutputGuardrailResult validate(OutputGuardrailRequest request) {
                guardrailCalls.incrementAndGet();
                return success();
            }

            @Override
            public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };

        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(
                        StreamingEventChatModelMock.thatStreams(AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("book")
                                .arguments("{\"arg0\": \"hotel\"}")
                                .build())))
                .chatMemory(memory)
                .tools(new Booking())
                .outputGuardrails(guardrail)
                .build();

        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        assistant.chat("Book a hotel").subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription s) {
                subscription.set(s);
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertThat(memory.toolResultPersistRequested.await(10, SECONDS)).isTrue();
        subscription.get().cancel();
        memory.toolResultPersisted.complete(null);

        // give a (wrongly) continued invocation the chance to run the guardrails
        Thread.sleep(200);

        assertThat(completed).isFalse();
        assertThat(guardrailCalls).hasValue(0);
    }
}
