package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InputGuardrailChainTests extends BaseGuardrailTests {
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
    void failTheChain() {
        var firstGuardrail = SingletonClassInstanceFactory.getInstance(FirstGuardrail.class);
        var secondGuardrail = SingletonClassInstanceFactory.getInstance(SecondGuardrail.class);
        var failingGuardrail = SingletonClassInstanceFactory.getInstance(FailingGuardrail.class);

        assertThatThrownBy(() -> aiService.failingFirstTwo("1", "foo"))
                .isInstanceOf(InputGuardrailException.class)
                .hasCauseInstanceOf(ValidationException.class)
                .hasRootCauseMessage("boom");
        assertThat(firstGuardrail.spy()).isEqualTo(1);
        assertThat(secondGuardrail.spy()).isEqualTo(0);
        assertThat(failingGuardrail.spy()).isEqualTo(1);
    }

    public interface MyAiService {
        @InputGuardrails({FirstGuardrail.class, SecondGuardrail.class})
        String firstOneTwo(@MemoryId String mem, @UserMessage String message);

        @InputGuardrails({SecondGuardrail.class, FirstGuardrail.class})
        String twoAndFirst(@MemoryId String mem, @UserMessage String message);

        @InputGuardrails({FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class})
        String failingFirstTwo(@MemoryId String mem, @UserMessage String message);

        static MyAiService create() {
            return createAiService(MyAiService.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    public static class FirstGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private final AtomicLong lastAccess = new AtomicLong();

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
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

    public static class SecondGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);
        private volatile AtomicLong lastAccess = new AtomicLong();

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
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

    public static class FailingGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            if (spy.incrementAndGet() == 1) {
                return fatal("boom", new ValidationException("boom"));
            }
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }
}
