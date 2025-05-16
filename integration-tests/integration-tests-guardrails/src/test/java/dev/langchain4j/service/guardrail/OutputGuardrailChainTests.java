package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    }

    @Test
    void guardrailOrderIsCorrect() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        aiService.twoAndFirst("1", "foo");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.lastAccess()).isLessThan(firstGuardrail.lastAccess());
    }

    @Test
    void retryRepromptRestartsTheChain() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        var failingGuardrail = SingletonClassInstanceFactory.getInstance(FailingGuardrail.class);
        aiService.failingFirstTwo("1", "foo");
        assertThat(firstGuardrail.spy()).isEqualTo(3);
        assertThat(secondGuardrail.spy()).isEqualTo(1);
        assertThat(failingGuardrail.spy()).isEqualTo(3);
        assertThat(firstGuardrail.lastAccess()).isLessThan(secondGuardrail.lastAccess());
    }

    @Test
    void rewritesTheOutputTwiceInTheChain() {
        assertThat(aiService.rewritingSuccess("1", "foo")).isEqualTo("Request: foo; Response: Hi!,1,2");
    }

    @Test
    void repromptAfterRewriteIsNotAllowed() {
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.repromptAfterRewrite("1", "foo"))
                .withMessageContaining("Retry or reprompt is not allowed after a rewritten output");
    }

    @Test
    void rewritesTheOutputWithAResult() {
        assertThat(aiService.rewritingSuccessWithResult("1", "foo")).isSameAs(RewritingGuardrailWithResult.RESULT);
    }

    public interface MyAiService {
        @OutputGuardrails({FirstGuardrail.class, SecondGuardrail.class})
        String firstOneTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({SecondGuardrail.class, FirstGuardrail.class})
        String twoAndFirst(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails(
                value = {FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class},
                config = @OutputGuardrailsConfig(maxRetries = 3))
        String failingFirstTwo(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstRewritingGuardrail.class, SecondRewritingGuardrail.class})
        String rewritingSuccess(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstRewritingGuardrail.class, RepromptingGuardrail.class})
        String repromptAfterRewrite(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails({FirstRewritingGuardrail.class, RewritingGuardrailWithResult.class})
        String rewritingSuccessWithResult(@MemoryId String mem, @UserMessage String message);

        static MyAiService create() {
            return createAiService(MyAiService.class);
        }
    }

    public static class FirstGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private volatile AtomicLong lastAccess = new AtomicLong();

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
    }

    public static class SecondGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private volatile AtomicLong lastAccess = new AtomicLong();

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
    }

    public static class FailingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

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
    }

    public static class FirstRewritingGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            String text = responseFromLLM.text();
            return successWith(text + ",1");
        }
    }

    public static class SecondRewritingGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            var text = responseFromLLM.text();
            return successWith(text + ",2");
        }
    }

    public static class RewritingGuardrailWithResult implements OutputGuardrail {
        static final String RESULT = String.valueOf(1_000);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            var text = responseFromLLM.text();
            return successWith(text + ",2", RESULT);
        }
    }

    public static class RepromptingGuardrail implements OutputGuardrail {
        private boolean firstCall = true;

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (firstCall) {
                firstCall = false;
                String text = responseFromLLM.text();
                return reprompt("Wrong message", text + ", " + text);
            }

            return success();
        }
    }
}
