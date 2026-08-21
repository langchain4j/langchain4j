package dev.langchain4j.model.mistralai;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.common.AbstractModerationModelListenerIT;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiModerationModelListenerIT extends AbstractModerationModelListenerIT {

    @Override
    protected ModerationModel createModel(List<ModerationModelListener> listeners) {
        return MistralAiModerationModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName("mistral-moderation-latest")
                .logRequests(true)
                .logResponses(true)
                .listeners(listeners)
                .build();
    }

    @Override
    protected ModerationModel createFailingModel(List<ModerationModelListener> listeners) {
        return MistralAiModerationModel.builder()
                .apiKey("banana")
                .modelName("mistral-moderation-latest")
                .maxRetries(0)
                .listeners(listeners)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return AuthenticationException.class;
    }
}
