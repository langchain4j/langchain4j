package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutputGuardrailRepromptingRetryTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void ok() {
        var okGuardrail = SingletonClassInstanceFactory.getInstance(OkGuardrail.class);
        aiService.ok("1", "foo");
        assertThat(okGuardrail.getSpy()).isEqualTo(1);

        aiService.ok("1", "bar");
        assertThat(okGuardrail.getSpy()).isEqualTo(2);
    }

    @Test
    void retryFailing() {
        var retryGuardrail = SingletonClassInstanceFactory.getInstance(RetryGuardrail.class);
        assertThatThrownBy(() -> aiService.retry("1", "foo"))
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(retryGuardrail.getSpy()).isEqualTo(5);
    }

    @Test
    void noRetry() {
        var retryGuardrail = SingletonClassInstanceFactory.getInstance(RetryGuardrail.class);
        retryGuardrail.reset();
        assertThatThrownBy(() -> aiService.noRetry("2", "foo"))
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(retryGuardrail.getSpy()).isEqualTo(1);
    }

    @Test
    void repromptingFailing() {
        var repromptingGuardrail = SingletonClassInstanceFactory.getInstance(RepromptingGuardrail.class);
        assertThatThrownBy(() -> aiService.reprompting("1", "foo"))
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("maximum number of retries");
        assertThat(repromptingGuardrail.getSpy())
                .isEqualTo(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.MAX_RETRIES_DEFAULT);
    }

    @SystemMessage("Say Hi!")
    public interface MyAiService {
        @OutputGuardrails(OkGuardrail.class)
        String ok(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails(value = RetryGuardrail.class, maxRetries = 5)
        String retry(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails(value = RetryGuardrail.class, maxRetries = 0)
        String noRetry(@MemoryId String mem, @UserMessage String message);

        @OutputGuardrails(RepromptingGuardrail.class)
        String reprompting(@MemoryId String mem, @UserMessage String message);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    public static class OkGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            spy.incrementAndGet();
            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class RetryGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            spy.incrementAndGet();
            return retry("Retry");
        }

        public int getSpy() {
            return spy.get();
        }

        public void reset() {
            this.spy.set(0);
        }
    }

    public static class RepromptingGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            int v = spy.incrementAndGet();
            return reprompt("Retry", "reprompt");
        }

        public int getSpy() {
            return spy.get();
        }
    }
}
