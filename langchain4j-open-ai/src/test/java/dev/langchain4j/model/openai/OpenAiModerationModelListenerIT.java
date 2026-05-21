package dev.langchain4j.model.openai;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.common.AbstractModerationModelListenerIT;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiModerationModelListenerIT extends AbstractModerationModelListenerIT {

    @Override
    protected ModerationModel createModel(List<ModerationModelListener> listeners) {
        return OpenAiModerationModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .logRequests(true)
                .logResponses(true)
                .listeners(listeners)
                .build();
    }

    @Override
    protected ModerationModel createFailingModel(List<ModerationModelListener> listeners) {
        return OpenAiModerationModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey("banana")
                .maxRetries(0)
                .listeners(listeners)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return AuthenticationException.class;
    }
}
