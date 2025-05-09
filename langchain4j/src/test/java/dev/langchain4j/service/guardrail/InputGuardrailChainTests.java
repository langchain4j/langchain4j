package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InputGuardrailChainTests {
    @ParameterizedTest
    @MethodSource("aiServices")
    void failsTheChain(AnAiService aiService) {
        assertThatThrownBy(() -> aiService.failingFirstTwo("foo"))
                .isInstanceOf(InputGuardrailException.class)
                .hasCauseInstanceOf(ValidationException.class)
                .hasRootCauseMessage("boom");
    }

    static List<AnAiService> aiServices() {
        return List.of(
                createAiServiceWithClassNames(),
                createAiServiceWithInstances(),
                createMethodLevelAnnotationAiService(),
                createClassLevelAnnotationAiService());
    }

    private static MyAnnotationMethodLevelAiService createMethodLevelAnnotationAiService() {
        return AiServices.create(MyAnnotationMethodLevelAiService.class, new MyChatModel());
    }

    private static MyAnnotationClassLevelAiService createClassLevelAnnotationAiService() {
        return AiServices.create(MyAnnotationClassLevelAiService.class, new MyChatModel());
    }

    private static MyAiService createAiServiceWithClassNames() {
        return AiServices.builder(MyAiService.class)
                .chatModel(new MyChatModel())
                .inputGuardrailClasses(FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class)
                .build();
    }

    private static MyAiService createAiServiceWithInstances() {
        return AiServices.builder(MyAiService.class)
                .chatModel(new MyChatModel())
                .inputGuardrails(new FirstGuardrail(), new FailingGuardrail(), new SecondGuardrail())
                .build();
    }

    @InputGuardrails({FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class})
    public interface MyAnnotationClassLevelAiService extends AnAiService {}

    public interface MyAiService extends AnAiService {}

    public interface MyAnnotationMethodLevelAiService extends AnAiService {
        @InputGuardrails({FirstGuardrail.class, FailingGuardrail.class, SecondGuardrail.class})
        @Override
        String failingFirstTwo(String message);
    }

    public interface AnAiService {
        String failingFirstTwo(String message);
    }

    public static class FirstGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage um) {
            return success();
        }
    }

    public static class SecondGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage um) {
            return success();
        }
    }

    public static class FailingGuardrail implements InputGuardrail {
        AtomicInteger spy = new AtomicInteger(0);

        @Override
        public InputGuardrailResult validate(UserMessage um) {
            if (spy.incrementAndGet() == 1) {
                return fatal("boom", new ValidationException("boom"));
            }

            return success();
        }
    }

    public static class MyChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("Hi!")).build();
        }
    }
}
