package dev.langchain4j.model.watsonx.it;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_MODEL", matches = ".+")
public class WatsonxGatewayChatModelListenerIT extends AbstractChatModelListenerIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_MODEL");
    static final String LISTENER_MODEL =
            getOrDefault(System.getenv("WATSONX_GATEWAY_LISTENER_MODEL"), "gemini-3.6-flash");

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return createChatModel(LISTENER_MODEL)
                .listeners(List.of(listener))
                .defaultRequestParameters(ChatRequestParameters.builder()
                        .modelName(modelName())
                        .temperature(temperature())
                        .topP(topP())
                        .maxOutputTokens(maxTokens())
                        .build())
                .build();
    }

    @Override
    protected String modelName() {
        return LISTENER_MODEL;
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return createChatModel("invalid-model").listeners(List.of(listener)).build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        // For an unknown model the gateway answers with HTTP 400 and a generic "Bad Request" error code, so the
        // exception mapper cannot narrow it down to ModelNotFoundException
        return InvalidRequestException.class;
    }

    private WatsonxGatewayChatModel.Builder createChatModel(String model) {
        return WatsonxGatewayChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .modelName(model)
                .timeout(Duration.ofSeconds(120));
    }
}
