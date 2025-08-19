package dev.langchain4j.model.watsonx;

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.core.auth.AuthenticationProvider;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import com.ibm.watsonx.ai.core.exeception.WatsonxException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelListenerIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxChatModelListenerIT extends AbstractChatModelListenerIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    static final AuthenticationProvider authProvider =
            IAMAuthenticator.builder().apiKey(API_KEY).build();

    @Override
    protected ChatModel createModel(ChatModelListener listener) {
        return WatsonxChatModel.builder()
                .service(createChatService("meta-llama/llama-4-maverick-17b-128e-instruct-fp8"))
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
        return "meta-llama/llama-4-maverick-17b-128e-instruct-fp8";
    }

    @Override
    protected ChatModel createFailingModel(ChatModelListener listener) {
        return WatsonxChatModel.builder()
                .service(createChatService("invalid-model"))
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected Class<? extends Exception> expectedExceptionClass() {
        return WatsonxException.class;
    }

    private ChatService createChatService(String model) {
        return ChatService.builder()
                .url(URL)
                .authenticationProvider(authProvider)
                .projectId(PROJECT_ID)
                .modelId(model)
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
