package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.atIndex;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OutputGuardrailChainTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void guardrailChainsAreInvoked() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        aiService.firstOneTwo("1", "foo");
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
    void guardrailOrderIsCorrect() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        aiService.twoAndFirst("1", "foo");
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
    void retryRepromptRestartsTheChain() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        var failingGuardrail = SingletonClassInstanceFactory.getInstance(FailingGuardrail.class);
        aiService.failingFirstTwo("1", "foo");
        assertThat(firstGuardrail.spy()).isEqualTo(3);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
        assertThat(failingGuardrail.spy()).isEqualTo(3);
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

    @Test
    void rewritesTheOutputTwiceInTheChain() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstRewritingGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondRewritingGuardrail.class);
        assertThat(aiService.rewritingSuccess("1", "foo")).isEqualTo("Request: foo; Response: Hi!,1,2");
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
    void repromptAfterRewriteIsNotAllowed() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstRewritingGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(RepromptingGuardrail.class);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.repromptAfterRewrite("1", "foo"))
                .withMessageContaining("Retry or reprompt is not allowed after a rewritten output");
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
    void rewritesTheOutputWithAResult() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstRewritingGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(RewritingGuardrailWithResult.class);
        assertThat(aiService.rewritingSuccessWithResult("1", "foo")).isSameAs(RewritingGuardrailWithResult.RESULT);
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

    public interface MyAiService {
        @OutputGuardrails({FirstGuardrail.class, SecondGuardrail.class})
        String firstOneTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({SecondGuardrail.class, FirstGuardrail.class})
        String twoAndFirst(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails(
                value = {FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class},
                maxRetries = 3)
        String failingFirstTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstRewritingGuardrail.class, SecondRewritingGuardrail.class})
        String rewritingSuccess(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstRewritingGuardrail.class, RepromptingGuardrail.class})
        String repromptAfterRewrite(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstRewritingGuardrail.class, RewritingGuardrailWithResult.class})
        String rewritingSuccessWithResult(@MemoryId String mem, @UserMessage String message);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatModel(new MyChatModel()));
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

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }

        public int spy() {
            return spy.get();
        }

        public long lastAccess() {
            return lastAccess.get();
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
            int v = spy.incrementAndGet();

            if (v == 1) {
                return reprompt("Reprompt", "Reprompt");
            } else if (v == 2) {
                return retry("Retry");
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

    public static class FirstRewritingGuardrail implements OutputGuardrail {
        private final AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            this.chatMemory.set(params.requestParams().chatMemory());
            return OutputGuardrail.super.validate(params);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            String text = responseFromLLM.text();
            return successWith(text + ",1");
        }

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }
    }

    public static class SecondRewritingGuardrail implements OutputGuardrail {
        private final AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            this.chatMemory.set(params.requestParams().chatMemory());
            return OutputGuardrail.super.validate(params);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            var text = responseFromLLM.text();
            return successWith(text + ",2");
        }

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }
    }

    public static class RewritingGuardrailWithResult implements OutputGuardrail {
        static final String RESULT = String.valueOf(1_000);
        private final AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            this.chatMemory.set(params.requestParams().chatMemory());
            return OutputGuardrail.super.validate(params);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            var text = responseFromLLM.text();
            return successWith(text + ",2", RESULT);
        }

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }
    }

    public static class RepromptingGuardrail implements OutputGuardrail {
        private boolean firstCall = true;
        private final AtomicReference<ChatMemory> chatMemory = new AtomicReference<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            this.chatMemory.set(params.requestParams().chatMemory());
            return OutputGuardrail.super.validate(params);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (firstCall) {
                firstCall = false;
                String text = responseFromLLM.text();
                return reprompt("Wrong message", text + ", " + text);
            }

            return success();
        }

        public ChatMemory chatMemory() {
            return chatMemory.get();
        }
    }
}
