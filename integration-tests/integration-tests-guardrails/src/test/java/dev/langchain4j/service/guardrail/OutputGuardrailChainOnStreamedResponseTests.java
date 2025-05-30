package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OutputGuardrailChainOnStreamedResponseTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void guardrailChainsAreInvoked() throws InterruptedException {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);

        var value = execute(() -> aiService.firstOneTwo("1", "foo"));
        assertThat(value).isEqualTo("Hi! World! ");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
        assertThat(firstGuardrail.chatMemory())
                .isNotNull()
                .extracting(ChatMemory::messages)
                .satisfies(messages -> assertThat(messages)
                        .isNotNull()
                        .hasSize(2)
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.USER),
                                atIndex(0))
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.AI),
                                atIndex(1)));
        assertThat(secondGuardrail.chatMemory())
                .isNotNull()
                .extracting(ChatMemory::messages)
                .satisfies(messages -> assertThat(messages)
                        .isNotNull()
                        .hasSize(2)
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.USER),
                                atIndex(0))
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.AI),
                                atIndex(1)));
    }

    @Test
    void guardrailOrderIsCorrect() throws InterruptedException {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);

        var value = execute(() -> aiService.twoAndFirst("1", "foo"));
        assertThat(value).isEqualTo("Hi! World! ");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.lastAccess()).isLessThan(firstGuardrail.lastAccess());
        assertThat(firstGuardrail.chatMemory())
                .isNotNull()
                .extracting(ChatMemory::messages)
                .satisfies(messages -> assertThat(messages)
                        .isNotNull()
                        .hasSize(2)
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.USER),
                                atIndex(0))
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.AI),
                                atIndex(1)));
        assertThat(secondGuardrail.chatMemory())
                .isNotNull()
                .extracting(ChatMemory::messages)
                .satisfies(messages -> assertThat(messages)
                        .isNotNull()
                        .hasSize(2)
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.USER),
                                atIndex(0))
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.AI),
                                atIndex(1)));
    }

    @Test
    void retryRestartsTheChain() throws InterruptedException {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        var failingGuardrail = SingletonClassInstanceFactory.getInstance(FailingGuardrail.class);
        var value = execute(() -> aiService.failingFirstTwo("1", "foo"));
        assertThat(value).isEqualTo("Hi! World! ");
        assertThat(firstGuardrail.spy()).isEqualTo(2);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
        assertThat(failingGuardrail.spy()).isEqualTo(2);
        assertThat(failingGuardrail.chatMemory())
                .isNotNull()
                .extracting(ChatMemory::messages)
                .satisfies(messages -> assertThat(messages)
                        .isNotNull()
                        .hasSize(2)
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.USER),
                                atIndex(0))
                        .satisfies(
                                message -> assertThat(message)
                                        .isNotNull()
                                        .extracting(ChatMessage::type)
                                        .isEqualTo(ChatMessageType.AI),
                                atIndex(1)));
    }

    public interface MyAiService {
        @OutputGuardrails({FirstGuardrail.class, SecondGuardrail.class})
        TokenStream firstOneTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({SecondGuardrail.class, FirstGuardrail.class})
        TokenStream twoAndFirst(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class})
        TokenStream failingFirstTwo(@MemoryId String mem, @UserMessage String message);

        static MyAiService create() {
            return createAiService(
                    MyAiService.class, builder -> builder.streamingChatModel(new MyStreamingChatModel()));
        }
    }

    public static class FirstGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private volatile AtomicLong lastAccess = new AtomicLong();
        private final AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            this.chatMemory.set(params.requestParams().chatMemory());
            return OutputGuardrail.super.validate(params);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            lastAccess.set(System.nanoTime());
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (InterruptedException e) {
                // Ignore me
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }

        public long lastAccess() {
            return lastAccess.get();
        }

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }
    }

    public static class SecondGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private volatile AtomicLong lastAccess = new AtomicLong();
        private final AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            this.chatMemory.set(params.requestParams().chatMemory());
            return OutputGuardrail.super.validate(params);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            lastAccess.set(System.nanoTime());
            try {
                TimeUnit.MILLISECONDS.sleep(1);
            } catch (InterruptedException e) {
                // Ignore me
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }

        public long lastAccess() {
            return lastAccess.get();
        }

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }
    }

    public static class FailingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private final AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            this.chatMemory.set(params.requestParams().chatMemory());
            return OutputGuardrail.super.validate(params);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (spy.incrementAndGet() == 1) {
                return reprompt("Retry", "Retry");
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }
    }

    public static class MyStreamingChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Hi!");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("World!");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("Hi! World! "))
                    .build());
        }
    }
}
