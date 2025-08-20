package dev.langchain4j.model.watsonx.it;

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.core.auth.AuthenticationProvider;
import com.ibm.watsonx.ai.core.auth.iam.IAMAuthenticator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxChatModelIT extends AbstractChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    static final AuthenticationProvider authProvider =
            IAMAuthenticator.builder().apiKey(API_KEY).build();

    @Override
    protected List<ChatModel> models() {
        return List.of(WatsonxChatModel.builder()
                .service(createChatService("meta-llama/llama-4-maverick-17b-128e-instruct-fp8"))
                .build());
    }

    @Override
    protected String customModelName() {
        return "ibm/granite-3-3-8b-instruct";
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return WatsonxChatModel.builder()
                .service(createChatService("ibm/granite-3-3-8b-instruct"))
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return WatsonxChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    public boolean supportsSingleImageInputAsPublicURL() {
        // Watsonx does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    @Override
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(ChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(WatsonxChatModel.builder()
                .service(createChatService("meta-llama/llama-3-3-70b-instruct"))
                .build());
    }

    @Override
    protected void should_respect_user_message(ChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_respect_user_message(WatsonxChatModel.builder()
                .service(createChatService("meta-llama/llama-3-3-70b-instruct"))
                .build());
    }

    @Override
    protected void should_respect_JSON_response_format(ChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_respect_JSON_response_format(WatsonxChatModel.builder()
                .service(createChatService("meta-llama/llama-3-3-70b-instruct"))
                .build());
    }

    @Override
    protected void should_respect_JSON_response_format_with_schema(ChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_respect_JSON_response_format_with_schema(WatsonxChatModel.builder()
                .service(createChatService("meta-llama/llama-3-3-70b-instruct"))
                .build());
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
