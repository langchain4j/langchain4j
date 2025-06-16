package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InputGuardrailOnClassTest extends BaseGuardrailTests {
    @ParameterizedTest
    @MethodSource("services")
    void guardrailsFromTheClassAreInvoked(String testDescription, MyAiService aiService) {
        var okGuardrail = SingletonClassInstanceFactory.getInstance(OKGuardrail.class);
        assertThat(okGuardrail.spy()).isEqualTo(0);
        aiService.hi("1");
        assertThat(okGuardrail.spy()).isEqualTo(1);
        aiService.hi("2");
        assertThat(okGuardrail.spy()).isEqualTo(2);
    }

    static Stream<Arguments> services() {
        return Stream.of(
                Arguments.of("Using AiServices builder", MyAiService.create()),
                Arguments.of("Using annotation at class level", MyAiServiceWithClassAnnotation.create()));
    }

    public interface MyAiService {
        @UserMessage("Say Hi!")
        String hi(@MemoryId String mem);

        static MyAiService create() {
            return createAiService(
                    MyAiService.class,
                    List.of(OKGuardrail.class),
                    List.of(),
                    builder -> builder.chatModel(new MyChatModel()));
        }
    }

    @InputGuardrails(OKGuardrail.class)
    public interface MyAiServiceWithClassAnnotation extends MyAiService {
        static MyAiService create() {
            return createAiService(
                    MyAiServiceWithClassAnnotation.class, builder -> builder.chatModel(new MyChatModel()));
        }
    }

    public static class OKGuardrail implements InputGuardrail {
        private final AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage ignored) {
            spy.incrementAndGet();
            return success();
        }

        public int spy() {
            return spy.get();
        }
    }
}
