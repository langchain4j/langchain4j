package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OutputGuardrailOnClassAndMethodTests extends BaseGuardrailTests {
    MyAiService aiService = MyAiService.create();

    @Test
    void properGuardrailsAreInvoked() {
        var okGuardrail = SingletonClassInstanceFactory.getInstance(OKGuardrail.class);
        var koGuardrail = SingletonClassInstanceFactory.getInstance(KOGuardrail.class);

        assertThat(okGuardrail.spy()).isEqualTo(0);
        aiService.hi("1");
        assertThat(okGuardrail.spy()).isEqualTo(1);
        aiService.hi("2");
        assertThat(okGuardrail.spy()).isEqualTo(2);
        assertThat(koGuardrail.spy()).isEqualTo(0);
    }

    @OutputGuardrails(KOGuardrail.class)
    public interface MyAiService {

        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        String hi(@MemoryId String mem);

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
}
