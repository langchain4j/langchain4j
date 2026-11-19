package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.StringKeyedClassMetadataProviderFactory;
import dev.langchain4j.classloading.ClassMetadataProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

/**
 * The guardrails of an AI service are found, and run, when the method keys of the {@code ClassMetadataProviderFactory}
 * are not {@link Method}s, in the blocking and in the non-blocking modes: input and output guardrails,
 * declared on the method or on the class.
 */
class StringKeyedGuardrailsTests {
    static final AtomicInteger KO_RUNS = new AtomicInteger();

    public interface MyAiService {
        @UserMessage("Say Hi!")
        @InputGuardrails(OKGuardrail.class)
        String hi();

        @UserMessage("Say Hi!")
        @InputGuardrails(KOGuardrail.class)
        String ko();

        @UserMessage("Say Hi!")
        @InputGuardrails(KOGuardrail.class)
        CompletableFuture<String> koAsync();

        @UserMessage("Say Hi!")
        CompletableFuture<String> noGuardrailsAsync();

        @UserMessage("Say Hi!")
        @OutputGuardrails(RewritingGuardrail.class)
        String rewritten();

        @UserMessage("Say Hi!")
        @OutputGuardrails(RewritingGuardrail.class)
        CompletableFuture<String> rewrittenAsync();
    }

    @InputGuardrails(KOGuardrail.class)
    public interface ClassLevelAiService {
        @UserMessage("Say Hi!")
        String hi();
    }

    public static class OKGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            return success();
        }

        @Override
        public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
            return CompletableFuture.completedFuture(success());
        }
    }

    public static class KOGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            KO_RUNS.incrementAndGet();
            return failure("KO");
        }

        @Override
        public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request.userMessage()));
        }
    }

    public static class RewritingGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith("Rewritten!");
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    private static final ChatModel CHAT_MODEL = MockChatModel.thatResponds("Hi!").thenRepeats();

    private final MyAiService aiService =
            AiServices.builder(MyAiService.class).chatModel(CHAT_MODEL).build();

    @Test
    void theFactoryWithStringKeysIsTheOneInUse() {
        assertThat(ClassMetadataProvider.getClassMetadataProviderFactory())
                .isInstanceOf(StringKeyedClassMetadataProviderFactory.class);
    }

    @Test
    void guardrailsAreFoundAndRunWhenInvokingBlocking() {
        var before = KO_RUNS.get();

        assertThat(aiService.hi()).isEqualTo("Hi!");
        assertThatThrownBy(aiService::ko).hasMessageContaining("KO");
        assertThat(KO_RUNS.get()).isEqualTo(before + 1);
    }

    @Test
    void guardrailsAreFoundAndRunWhenInvokingNonBlocking() {
        var before = KO_RUNS.get();

        assertThat(aiService.koAsync())
                .failsWithin(Duration.ofSeconds(10))
                .withThrowableThat()
                .withMessageContaining("KO");
        assertThat(KO_RUNS.get()).isEqualTo(before + 1);
        assertThat(aiService.noGuardrailsAsync())
                .succeedsWithin(Duration.ofSeconds(10))
                .isEqualTo("Hi!");
    }

    @Test
    void outputGuardrailsAreFoundAndRunWhenInvokingBlockingAndNonBlocking() {
        assertThat(aiService.rewritten()).isEqualTo("Rewritten!");
        assertThat(aiService.rewrittenAsync())
                .succeedsWithin(Duration.ofSeconds(10))
                .isEqualTo("Rewritten!");
    }

    @Test
    void classLevelGuardrailsAreFoundAndRun() {
        var before = KO_RUNS.get();
        var classLevelAiService = AiServices.builder(ClassLevelAiService.class)
                .chatModel(CHAT_MODEL)
                .build();

        assertThatThrownBy(classLevelAiService::hi).hasMessageContaining("KO");
        assertThat(KO_RUNS.get()).isEqualTo(before + 1);
    }
}
