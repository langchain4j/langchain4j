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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutputGuardrailValidationTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void ok() {
        var okGuardrail = SingletonClassInstanceFactory.getInstance(OKGuardrail.class);
        aiService.ok("1");
        assertThat(okGuardrail.spy()).isOne();
    }

    @Test
    void ko() {
        var koGuardrail = SingletonClassInstanceFactory.getInstance(KOGuardrail.class);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.ko("2"))
                .withMessageContaining("KO");

        assertThat(koGuardrail.spy()).isOne();
    }

    @Test
    void retryOk() {
        var retry = SingletonClassInstanceFactory.getInstance(RetryingGuardrail.class);
        aiService.retry("3");
        assertThat(retry.spy()).isEqualTo(2);
    }

    @Test
    void retryFail() {
        var retryFail = SingletonClassInstanceFactory.getInstance(RetryingButFailGuardrail.class);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.retryButFail("4"))
                .withMessageContaining("maximum number of retries");
        assertThat(retryFail.spy()).isEqualTo(3);
    }

    @Test
    void fatalException() {
        var fatal = SingletonClassInstanceFactory.getInstance(KOFatalGuardrail.class);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> aiService.fatal("5"))
                .withMessageContaining("Fatal");
        assertThat(fatal.spy()).isOne();
    }

    public interface MyAiService {
        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        String ok(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOGuardrail.class)
        String ko(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(RetryingGuardrail.class)
        String retry(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(value = RetryingButFailGuardrail.class, maxRetries = 3)
        String retryButFail(@MemoryId String mem);

        @UserMessage("Say Hi!")
        @OutputGuardrails(KOFatalGuardrail.class)
        String fatal(@MemoryId String mem);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    public static class OKGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class KOGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return failure("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class RetryingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            int v = spy.incrementAndGet();
            if (v == 2) {
                return OutputGuardrailResult.success();
            }
            return retry("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class RetryingButFailGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            int v = spy.incrementAndGet();
            return retry("KO");
        }

        public int spy() {
            return spy.get();
        }
    }

    public static class KOFatalGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            throw new IllegalArgumentException("Fatal");
        }

        public int spy() {
            return spy.get();
        }
    }
}
