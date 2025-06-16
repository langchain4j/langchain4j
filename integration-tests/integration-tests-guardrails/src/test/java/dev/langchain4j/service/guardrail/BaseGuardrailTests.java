package dev.langchain4j.service.guardrail;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseGuardrailTests {
    @BeforeEach
    void beforeEach() {
        SingletonClassInstanceFactory.clearInstances();
    }

    static String execute(Supplier<TokenStream> aiServiceInvocation) throws InterruptedException {
        var latch = new CountDownLatch(1);
        var value = new AtomicReference<String>();

        aiServiceInvocation
                .get()
                .onError(t -> {
                    throw new RuntimeException(t);
                })
                .onPartialResponse(token -> {})
                .onCompleteResponse(response -> {
                    value.set(response.aiMessage().text());
                    latch.countDown();
                })
                .start();

        latch.await(10, TimeUnit.SECONDS);

        return value.get();
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
