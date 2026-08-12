package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_DEPLOYMENT_ID");

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(createStreamingChatModel("mistralai/mistral-medium-2505").build());
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
    protected void should_execute_a_tool_then_answer(StreamingChatModel model) {
        super.should_execute_a_tool_then_answer(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected void should_execute_a_tool_without_arguments_then_answer(StreamingChatModel model) {
        super.should_execute_a_tool_without_arguments_then_answer(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected void should_execute_multiple_tools_in_parallel_then_answer(StreamingChatModel model) {
        super.should_execute_multiple_tools_in_parallel_then_answer(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected void should_force_LLM_to_execute_any_tool(StreamingChatModel model) {
        super.should_force_LLM_to_execute_any_tool(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected void should_force_LLM_to_execute_specific_tool(StreamingChatModel model) {
        super.should_force_LLM_to_execute_specific_tool(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    public boolean supportsSingleImageInputAsPublicURL() {
        // Watsonx does not support images as URLs, only as Base64-encoded strings
        return false;
    }

    @Override
    protected boolean supportsStreamingCancellation() {
        return false;
    }

    @Override
    protected void should_respect_user_message(StreamingChatModel model) {
        super.should_respect_user_message(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected void should_respect_JSON_response_format(StreamingChatModel model) {
        super.should_respect_JSON_response_format(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(
            StreamingChatModel model) {
        super.should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(
                createStreamingChatModel("ibm/granite-4-h-small").build());
    }

    @Override
    protected void should_respect_JSON_response_format_with_schema(StreamingChatModel model) {
        super.should_respect_JSON_response_format_with_schema(
                createStreamingChatModel("ibm/granite-4-h-small").build());
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

    @Test
    @EnabledIfEnvironmentVariable(named = "WATSONX_DEPLOYMENT_ID", matches = ".+")
    void should_use_deployed_model_with_deployment_id() {
        var chatModel = WatsonxStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(DEPLOYMENT_ID)
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<ChatResponse>(null);

        chatModel.chat("Hello", new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                latch.countDown();
                chatResponse.set(completeResponse);
            }

            @Override
            public void onError(Throwable error) {}
        });

        assertDoesNotThrow(() -> latch.await(5, TimeUnit.SECONDS));
        assertNotNull(chatResponse.get().aiMessage().text());
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
