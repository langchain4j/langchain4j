package dev.langchain4j.model.watsonx.it;

import static dev.langchain4j.model.watsonx.it.WatsonxToolCallbacksVerifier.verifyToolCall;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxDeploymentStreamingChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_DEPLOYMENT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GEMMA_DEPLOYMENT_ID", matches = ".+")
public class WatsonxDeploymentStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_DEPLOYMENT_ID");
    static final String NO_REASONING_DEPLOYMENT_ID = System.getenv("WATSONX_GRANITE_3_3_DEPLOYMENT_ID");
    static final String VISION_DEPLOYMENT_ID = System.getenv("WATSONX_GEMMA_DEPLOYMENT_ID");

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(createStreamingChatModel(DEPLOYMENT_ID).build());
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingTools() {
        // The reasoning model rejects the tools created by the base test class, because it requires a description
        // for every tool
        return List.of(createStreamingChatModel(NO_REASONING_DEPLOYMENT_ID).build());
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingImageInputs() {
        return List.of(createStreamingChatModel(VISION_DEPLOYMENT_ID).build());
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        // Used by the "in_default_model_parameters" tests, which assert on maxOutputTokens and stopSequences
        return createStreamingChatModel(NO_REASONING_DEPLOYMENT_ID)
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return createStreamingChatModel(DEPLOYMENT_ID)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return WatsonxChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
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
    protected boolean supportsStreamingCancellation() {
        // The watsonx models do not expose a StreamingHandle that can stop an ongoing stream
        return false;
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        // None of the deployed models can combine the two features. The reasoning one rejects tools without a
        // description, the others answer with the requested JSON instead of calling the tool
        return false;
    }

    @Override
    protected boolean assertThreads() {
        // The watsonx.ai SDK dispatches every callback of a response through a virtual thread per task executor, so
        // the callbacks stay sequential but do not share a single thread
        return false;
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void should_respect_maxOutputTokens_in_chat_request(StreamingChatModel model) {
        super.should_respect_maxOutputTokens_in_chat_request(noReasoningStreamingChatModel());
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            StreamingChatModel model) {
        super.should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
                noReasoningStreamingChatModel());
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(StreamingChatModel model) {
        super.should_respect_stopSequences_in_chat_request(noReasoningStreamingChatModel());
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        verifyToolCall(handler, io, 0, id, "getWeather", "{\"city\": \"Munich\"}");
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        verifyToolCallbacks(handler, io, id1);
        verifyToolCall(handler, io, 1, id2, "getTime", "{\"country\": \"France\"}");
    }

    private StreamingChatModel noReasoningStreamingChatModel() {
        return createStreamingChatModel(NO_REASONING_DEPLOYMENT_ID).build();
    }

    private WatsonxDeploymentStreamingChatModel.Builder createStreamingChatModel(String deploymentId) {
        return WatsonxDeploymentStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(deploymentId)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(120));
    }
}
