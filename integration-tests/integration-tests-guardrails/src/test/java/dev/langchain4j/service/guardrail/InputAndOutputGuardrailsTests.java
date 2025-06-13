package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InputAndOutputGuardrailsTests extends BaseGuardrailTests {
    MyAiService service = MyAiService.create();

    @Test
    void ok() {
        var okIn = SingletonClassInstanceFactory.getInstance(MyOkInputGuardrail.class);
        var okOut = SingletonClassInstanceFactory.getInstance(MyOkOutputGuardrail.class);
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(okOut.getSpy()).isEqualTo(0);
        service.bothOk("1", "foo");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(okOut.getSpy()).isEqualTo(1);
    }

    @Test
    void inKo() {
        var koIn = SingletonClassInstanceFactory.getInstance(MyKoInputGuardrail.class);
        var okOut = SingletonClassInstanceFactory.getInstance(MyOkOutputGuardrail.class);
        assertThat(koIn.getSpy()).isEqualTo(0);
        assertThat(okOut.getSpy()).isEqualTo(0);

        assertThatExceptionOfType(InputGuardrailException.class)
                .isThrownBy(() -> service.inKo("2", "foo"))
                .withCauseExactlyInstanceOf(ValidationException.class)
                .havingRootCause()
                .withMessage("boom");
        assertThat(koIn.getSpy()).isEqualTo(1);
        assertThat(okOut.getSpy()).isEqualTo(0);
    }

    @Test
    void outKo() {
        var okIn = SingletonClassInstanceFactory.getInstance(MyOkInputGuardrail.class);
        var koOut = SingletonClassInstanceFactory.getInstance(MyKoOutputGuardrail.class);
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(koOut.getSpy()).isEqualTo(0);
        assertThatExceptionOfType(OutputGuardrailException.class)
                .isThrownBy(() -> service.outKo("2", "foo"))
                .withCauseExactlyInstanceOf(ValidationException.class)
                .havingRootCause()
                .withMessage("boom");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(koOut.getSpy()).isEqualTo(1);
    }

    @Test
    void retry() {
        var okIn = SingletonClassInstanceFactory.getInstance(MyOkInputGuardrail.class);
        var koOutWithRetry = SingletonClassInstanceFactory.getInstance(MyKoWithRetryOutputGuardrail.class);
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(koOutWithRetry.getSpy()).isEqualTo(0);
        service.outKoWithRetry("2", "foo");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(koOutWithRetry.getSpy()).isEqualTo(2);
    }

    @Test
    void reprompt() {
        var okIn = SingletonClassInstanceFactory.getInstance(MyOkInputGuardrail.class);
        var koOutWithReprompt = SingletonClassInstanceFactory.getInstance(MyKoWithRepromprOutputGuardrail.class);
        assertThat(okIn.getSpy()).isEqualTo(0);
        assertThat(koOutWithReprompt.getSpy()).isEqualTo(0);
        service.outKoWithReprompt("2", "foo");
        assertThat(okIn.getSpy()).isEqualTo(1);
        assertThat(koOutWithReprompt.getSpy()).isEqualTo(2);
    }

    public interface MyAiService {
        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyOkOutputGuardrail.class)
        String bothOk(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyKoInputGuardrail.class)
        @OutputGuardrails(MyOkOutputGuardrail.class)
        String inKo(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyKoOutputGuardrail.class)
        String outKo(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyKoWithRetryOutputGuardrail.class)
        String outKoWithRetry(@MemoryId String id, @UserMessage String message);

        @InputGuardrails(MyOkInputGuardrail.class)
        @OutputGuardrails(MyKoWithRepromprOutputGuardrail.class)
        String outKoWithReprompt(@MemoryId String id, @UserMessage String message);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    public static class MyOkInputGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger();

        @Override
        public InputGuardrailResult validate(InputGuardrailRequest params) {
            spy.incrementAndGet();
            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyKoInputGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger();

        @Override
        public InputGuardrailResult validate(InputGuardrailRequest params) {
            spy.incrementAndGet();
            return failure("boom", new ValidationException("boom"));
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyOkOutputGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            spy.incrementAndGet();
            return OutputGuardrailResult.success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyKoOutputGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            spy.incrementAndGet();
            return failure("boom", new ValidationException("boom"));
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyKoWithRetryOutputGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            if (spy.incrementAndGet() == 1) {
                return retry("KO");
            }
            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyKoWithRepromprOutputGuardrail implements OutputGuardrail {
        private final AtomicInteger spy = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest params) {
            if (spy.incrementAndGet() == 1) {
                return reprompt("KO", "retry");
            }
            return success();
        }

        public int getSpy() {
            return spy.get();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("Hi!")).build();
        }
    }
}
