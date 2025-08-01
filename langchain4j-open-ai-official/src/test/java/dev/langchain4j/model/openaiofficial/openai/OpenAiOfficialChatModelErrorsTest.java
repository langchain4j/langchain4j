package dev.langchain4j.model.openaiofficial.openai;

import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import java.time.Duration;
import me.kpavlov.aimocks.core.AbstractBuildingStep;
import me.kpavlov.aimocks.openai.MockOpenai;
import org.jspecify.annotations.Nullable;

class OpenAiOfficialChatModelErrorsTest extends AbstractChatModelErrorsTest<OpenAiOfficialChatModel, MockOpenai> {

    OpenAiOfficialChatModelErrorsTest() {
        super(new MockOpenai(0, true));
    }

    @Override
    protected OpenAiOfficialChatModel createModel(final double temperature, final @Nullable Duration timeout) {
        final var modelBuilder = OpenAiOfficialChatModel.builder()
                .apiKey("dummy-api-key")
                .modelName(com.openai.models.ChatModel.GPT_4_1.asString())
                .baseUrl(mock.baseUrl())
                .maxRetries(0);
        if (timeout != null) {
            modelBuilder.timeout(timeout);
        }
        return modelBuilder.build();
    }

    @Override
    protected AbstractBuildingStep<?, ?> whenMockMatched(final String question, final double temperature) {
        return mock.completion(req -> {
            req.userMessageContains(question);
            req.temperature(temperature);
        });
    }
}
