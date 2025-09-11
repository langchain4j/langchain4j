package dev.langchain4j.model.watsonx.it;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
        return List.of(createStreamingChatModel("meta-llama/llama-4-maverick-17b-128e-instruct-fp8")
                .build());
    }

    @Override
    protected String customModelName() {
        return "ibm/granite-3-3-8b-instruct";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return WatsonxChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return createStreamingChatModel("ibm/granite-3-3-8b-instruct")
                .defaultRequestParameters(parameters)
                .build();
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return createStreamingChatModel("ibm/granite-3-3-8b-instruct")
                .listeners(List.of(listener))
                .build();
    }

    @Override
    public boolean supportsSingleImageInputAsPublicURL() {
        // Watsonx does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    @Override
    protected void should_accept_single_image_as_base64_encoded_string(StreamingChatModel model) {
        super.should_respect_user_message(
                createStreamingChatModel("mistralai/mistral-medium-2505").build());
    }

    @Override
    protected void should_respect_user_message(StreamingChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_respect_user_message(
                createStreamingChatModel("meta-llama/llama-3-3-70b-instruct").build());
    }

    @Override
    protected void should_respect_JSON_response_format(StreamingChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_respect_JSON_response_format(
                createStreamingChatModel("meta-llama/llama-3-3-70b-instruct").build());
    }

    @Override
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(
            StreamingChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(
                createStreamingChatModel("meta-llama/llama-3-3-70b-instruct").build());
    }

    @Override
    protected void should_respect_JSON_response_format_with_schema(StreamingChatModel model) {
        // Maverick doesn't work for this test. It is better to use meta-llama/llama-3-3-70b-instruct instead.
        super.should_respect_JSON_response_format_with_schema(
                createStreamingChatModel("meta-llama/llama-3-3-70b-instruct").build());
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "{\"city\": \""));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "Mun"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "ich"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "\"}"));
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\": \"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        verifyToolCallbacks(handler, io, id1);
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "{\"country\": \""));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "France"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "\"}"));
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\": \"France\"}"));
    }

    private WatsonxStreamingChatModel.Builder createStreamingChatModel(String model) {
        return WatsonxStreamingChatModel.builder()
                .url(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(model)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .timeLimit(Duration.ofSeconds(30));
    }
}
