package dev.langchain4j.model.watsonx.it;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DEPLOYMENT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GEMMA_DEPLOYMENT_ID", matches = ".+")
public class WatsonxDeploymentChatModelIT extends AbstractChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_DEPLOYMENT_ID");
    static final String NO_REASONING_DEPLOYMENT_ID = System.getenv("WATSONX_GRANITE_3_3_DEPLOYMENT_ID");
    static final String VISION_DEPLOYMENT_ID = System.getenv("WATSONX_GEMMA_DEPLOYMENT_ID");

    @Override
    protected List<ChatModel> models() {
        return List.of(createChatModel(DEPLOYMENT_ID).build());
    }

    @Override
    protected List<ChatModel> modelsSupportingTools() {
        // The reasoning model rejects the tools created by the base test class, because it requires a description
        // for every tool
        return List.of(createChatModel(NO_REASONING_DEPLOYMENT_ID).build());
    }

    @Override
    protected List<ChatModel> modelsSupportingImageInputs() {
        return List.of(createChatModel(VISION_DEPLOYMENT_ID).build());
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        // Used by the "in_default_model_parameters" tests, which assert on maxOutputTokens and stopSequences
        return createChatModel(NO_REASONING_DEPLOYMENT_ID)
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
    protected boolean supportsModelNameParameter() {
        // The deployment id already defines the model to call
        return false;
    }

    @Override
    public boolean supportsSingleImageInputAsPublicURL() {
        // Watsonx does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        // None of the deployed models can combine the two features. The reasoning one rejects tools without a
        // description, the others answer with the requested JSON instead of calling the tool
        return false;
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void should_respect_maxOutputTokens_in_chat_request(ChatModel model) {
        super.should_respect_maxOutputTokens_in_chat_request(noReasoningChatModel());
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            ChatModel model) {
        super.should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
                noReasoningChatModel());
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(ChatModel model) {
        super.should_respect_stopSequences_in_chat_request(noReasoningChatModel());
    }

    private ChatModel noReasoningChatModel() {
        return createChatModel(NO_REASONING_DEPLOYMENT_ID).build();
    }

    private WatsonxDeploymentChatModel.Builder createChatModel(String deploymentId) {
        return WatsonxDeploymentChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(deploymentId)
                .timeout(Duration.ofSeconds(120));
    }
}
