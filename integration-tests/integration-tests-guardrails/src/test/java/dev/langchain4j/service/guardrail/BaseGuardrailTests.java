package dev.langchain4j.service.guardrail;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseGuardrailTests {
    @BeforeEach
    void beforeEach() {
        SingletonClassInstanceFactory.clearInstances();
    }

    static String execute(Supplier<TokenStream> aiServiceInvocation) throws InterruptedException {
        var result = new CompletableFuture<String>();

        aiServiceInvocation
                .get()
                .onError(result::completeExceptionally)
                .onPartialResponse(token -> {})
                .onCompleteResponse(response -> result.complete(response.aiMessage().text()))
                .start();

        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("The AI Service did not complete within 10 seconds", e);
        }
    }

    static <T> T createAiService(Class<T> clazz, Function<AiServices<T>, AiServices<T>> builderCustomizer) {
        return createAiService(clazz, List.of(), List.of(), builderCustomizer);
    }

    static <T> T createAiService(Class<T> clazz) {
        return createAiService(clazz, Function.identity());
    }

    static <T, I extends InputGuardrail, O extends OutputGuardrail> T createAiService(
            Class<T> clazz,
            List<Class<? extends I>> inputGuardrailClasses,
            List<Class<? extends O>> outputGuardrailClasses) {

        return createAiService(clazz, inputGuardrailClasses, outputGuardrailClasses, Function.identity());
    }

    static <T, I extends InputGuardrail, O extends OutputGuardrail> T createAiService(
            Class<T> clazz,
            List<Class<? extends I>> inputGuardrailClasses,
            List<Class<? extends O>> outputGuardrailClasses,
            Function<AiServices<T>, AiServices<T>> builderCustomizer) {

        var builder = AiServices.builder(clazz)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .inputGuardrailClasses(inputGuardrailClasses)
                .outputGuardrailClasses(outputGuardrailClasses);

        return builderCustomizer.apply(builder).build();
    }
}
