package dev.langchain4j.model.watsonx.it;

import com.ibm.watsonx.ai.detection.detector.Hap;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.common.AbstractModerationModelListenerIT;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.watsonx.WatsonxModerationModel;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxModerationModelListenerIT extends AbstractModerationModelListenerIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @Override
    protected ModerationModel createModel(List<ModerationModelListener> listeners) {
        return WatsonxModerationModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .detectors(Hap.ofDefaults())
                .listeners(listeners)
                .build();
    }

    @Override
    protected ModerationModel createFailingModel(List<ModerationModelListener> listeners) {
        return WatsonxModerationModel.builder()
                .baseUrl(URL)
                .apiKey("invalid-api-key")
                .projectId(PROJECT_ID)
                .detectors(Hap.ofDefaults())
                .listeners(listeners)
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return AuthenticationException.class;
    }
}
