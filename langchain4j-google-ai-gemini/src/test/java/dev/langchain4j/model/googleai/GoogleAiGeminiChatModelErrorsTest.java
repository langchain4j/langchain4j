package dev.langchain4j.model.googleai;

import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest;
import java.time.Duration;
import me.kpavlov.aimocks.core.AbstractBuildingStep;
import me.kpavlov.aimocks.gemini.MockGemini;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class GoogleAiGeminiChatModelErrorsTest extends AbstractChatModelErrorsTest<GoogleAiGeminiChatModel, MockGemini> {

    protected GoogleAiGeminiChatModelErrorsTest() {
        super(new MockGemini(0, true));
    }

    public static final String MODEL_NAME = "gemini-2.0-flash";

    @Override
    protected GoogleAiGeminiChatModel createModel(final double temperature, final @Nullable Duration timeout) {
        final var modelBuilder = GoogleAiGeminiChatModel.builder()
                .apiKey("dummy-api-key")
                .modelName(MODEL_NAME)
                .baseUrl(mock.baseUrl())
                .maxRetries(0);
        if (timeout != null) {
            modelBuilder.timeout(timeout);
        }
        return modelBuilder.build();
    }

    @Override
    protected AbstractBuildingStep<?, ?> whenMockMatched(final String question, final double temperature) {
        return mock.generateContent(req -> {
            req.userMessageContains(question);
            req.temperature(temperature);
            req.path("/models/%s:generateContent".formatted(MODEL_NAME));
        });
    }
}
