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
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(createStreamingChatModel("mistralai/mistral-small-3-1-24b-instruct-2503")
                .build());
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingTools() {
        return List.of(createStreamingChatModel("meta-llama/llama-4-maverick-17b-128e-instruct-fp8")
                .build());
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingStructuredOutputs() {
        return List.of(createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected String customModelName() {
        return "ibm/granite-4-h-small";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return WatsonxChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return createStreamingChatModel("ibm/granite-4-h-small")
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return createStreamingChatModel("ibm/granite-4-h-small")
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
        return WatsonxChatResponseMetadata.class;
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
        // None of the models available in the project can combine the two features. They either answer with the
        // requested JSON instead of calling the tool, or return a "tool_calls" finish reason without any tool call
        return false;
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_respect_user_message(StreamingChatModel model) {
        super.should_respect_user_message(
                createStreamingChatModel("ibm/granite-4-h-small").build());
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

    @Override
    protected boolean assertThreads() {
        // The watsonx.ai SDK dispatches every callback of a response through a virtual thread per task executor, so
        // the callbacks stay sequential but do not share a single thread
        return false;
    }

    private WatsonxStreamingChatModel.Builder createStreamingChatModel(String model) {
        return WatsonxStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(model)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30));
    }
}
