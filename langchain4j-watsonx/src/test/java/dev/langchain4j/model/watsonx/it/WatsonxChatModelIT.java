package dev.langchain4j.model.watsonx.it;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxChatModelIT extends AbstractChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @Override
    protected List<ChatModel> models() {
        return List.of(
                createChatModel("mistralai/mistral-small-3-1-24b-instruct-2503").build());
    }

    @Override
    protected List<ChatModel> modelsSupportingTools() {
        return List.of(createChatModel("meta-llama/llama-4-maverick-17b-128e-instruct-fp8")
                .build());
    }

    @Override
    protected List<ChatModel> modelsSupportingStructuredOutputs() {
        return List.of(createChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected String customModelName() {
        return "ibm/granite-4-h-small";
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return createChatModel("ibm/granite-4-h-small")
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
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return WatsonxChatResponseMetadata.class;
    }

    @Override
    public boolean supportsSingleImageInputAsPublicURL() {
        // Watsonx does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        // None of the models available in the project can combine the two features. They either answer with the
        // requested JSON instead of calling the tool, or return a "tool_calls" finish reason without any tool call
        return false;
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_respect_user_message(ChatModel model) {
        super.should_respect_user_message(
                createChatModel("ibm/granite-4-h-small").build());
    }

    private WatsonxChatModel.Builder createChatModel(String model) {
        return WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(model)
                .timeout(Duration.ofSeconds(30));
    }
}
