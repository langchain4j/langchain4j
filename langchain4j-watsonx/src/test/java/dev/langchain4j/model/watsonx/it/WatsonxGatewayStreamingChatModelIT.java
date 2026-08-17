package dev.langchain4j.model.watsonx.it;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.watsonx.it.WatsonxToolCallbacksVerifier.verifyToolCall;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxGatewayStreamingChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_MODEL", matches = ".+")
public class WatsonxGatewayStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_MODEL");
    static final String CUSTOM_MODEL = getOrDefault(System.getenv("WATSONX_GATEWAY_CUSTOM_MODEL"), "claude-sonnet-5");
    static final String STOP_SEQUENCES_MODEL =
            getOrDefault(System.getenv("WATSONX_GATEWAY_STOP_SEQUENCES_MODEL"), "gemini-3.6-flash");

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(createStreamingChatModel(MODEL).build());
    }

    @Override
    protected String customModelName() {
        return CUSTOM_MODEL;
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        String defaultModel = isNullOrEmpty(parameters.stopSequences()) ? MODEL : STOP_SEQUENCES_MODEL;
        return createStreamingChatModel(getOrDefault(parameters.modelName(), defaultModel))
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(StreamingChatModel model) {
        super.should_respect_stopSequences_in_chat_request(
                createStreamingChatModel(STOP_SEQUENCES_MODEL).build());
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return createStreamingChatModel(MODEL).listeners(List.of(listener)).build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return WatsonxGatewayChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
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

    private WatsonxGatewayStreamingChatModel.Builder createStreamingChatModel(String model) {
        return WatsonxGatewayStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .modelName(model)
                .timeout(Duration.ofSeconds(120));
    }
}
