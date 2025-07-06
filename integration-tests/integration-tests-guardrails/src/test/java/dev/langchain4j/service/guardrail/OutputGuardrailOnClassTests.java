package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutputGuardrailOnClassTests extends BaseGuardrailTests {
    @ParameterizedTest
    @MethodSource("services")
    void correctGuardrailsAreInvoked(String testDescription, MyAiService aiService) {
        var okGuardrail = SingletonClassInstanceFactory.getInstance(OKGuardrail.class);
        var koGuardrail = SingletonClassInstanceFactory.getInstance(KOGuardrail.class);
        assertThat(okGuardrail.spy()).isEqualTo(0);
        aiService.hi("1");
        assertThat(okGuardrail.spy()).isEqualTo(1);
        aiService.hi("2");
        assertThat(okGuardrail.spy()).isEqualTo(2);
        assertThat(koGuardrail.spy()).isEqualTo(0);
    }

    static Stream<Arguments> services() {
        return Stream.of(
                Arguments.of("AiService using builder", MyAiService.create()),
                Arguments.of("AiService using annotation at class level", MyAiServiceWithClassAnnotation.create()),
                Arguments.of(
                        "AiService using annotation at class and method level",
                        MyAiServiceWithClassAndMethodAnnotation.create()));
    }

    public interface MyAiService {
        @UserMessage("Say Hi!")
        String hi(@MemoryId String mem);

        static MyAiService create() {
            return createAiService(
                    MyAiService.class,
                    List.of(),
                    List.of(OKGuardrail.class),
                    builder -> builder.chatModel(new MyChatModel()));
        }
    }

    @OutputGuardrails(OKGuardrail.class)
    public interface MyAiServiceWithClassAnnotation extends MyAiService {
        static MyAiService create() {
            return createAiService(
                    MyAiServiceWithClassAnnotation.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    @OutputGuardrails(KOGuardrail.class)
    public interface MyAiServiceWithClassAndMethodAnnotation extends MyAiService {
        @Override
        @UserMessage("Say Hi!")
        @OutputGuardrails(OKGuardrail.class)
        String hi(@MemoryId String mem);

        static MyAiService create() {
            return createAiService(
                    MyAiServiceWithClassAndMethodAnnotation.class, builder -> builder.chatModel(new MyChatModel()));
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
}
