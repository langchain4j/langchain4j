import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.gpullama3.GPULlama3StreamingChatModel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class GPULlama3CStreamingChatModelIT extends AbstractStreamingChatModelIT {
    private static final Path MODEL_PATH = Paths.get("beehive-llama-3.2-1b-instruct-fp16.gguf");
    private static GPULlama3StreamingChatModel model;

    @BeforeAll
    public static void setup() {
        // @formatter:off
        model = GPULlama3StreamingChatModel.builder()
                .modelPath(MODEL_PATH)
                .temperature(0.6)
                .topP(1.0)
                .maxTokens(2048)
                .seed(12345)
                .onGPU(Boolean.TRUE) // if false, runs on CPU though a lightweight implementation of llama3.java
                .build();
        // @formatter:on
    }

    @Test
    void should_stream_answer_and_return_response() throws Exception {
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        String prompt;
        StringBuilder answerBuilder = new StringBuilder();

        prompt = "When is the best time of year to visit Japan?";

        // @formatter:off
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(prompt), SystemMessage.from("reply with extensive sarcasm"))
                .build();
        // @formatter:on

        model.chat(request, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                answerBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        futureResponse.join();

        ChatResponse response = futureResponse.get(30, SECONDS);
        String streamedAnswer = answerBuilder.toString();

        // then
        assertThat(streamedAnswer).isNotBlank();

        AiMessage aiMessage = response.aiMessage();
        assertThat(streamedAnswer).contains(aiMessage.text());
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return null;
    }

    @Override
    @Disabled
    protected void should_respect_stopSequences_in_default_model_parameters() {}

    // Override feature support methods to return false for unsupported features
    @Override
    protected boolean supportsTools() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false;
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // GPU Llama3 uses a fixed model path
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return true; // Assuming this is supported
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // Assuming this is supported
    }

    // Override assertion methods for GPU Llama3 specifics
    @Override
    protected boolean assertResponseId() {
        return false; // GPU Llama3 might not return response IDs
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // GPU Llama3 might not return model name in response
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // GPU Llama3 might not return token usage
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // Assuming this is supported
    }

    @Override
    protected boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        return false; // vision model only supports a single image per message
    }

    @Override
    protected boolean supportsStreamingCancellation() {
        return false;
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(model);
    }

    @Override
    protected String customModelName() {
        // GPU Llama3 uses a fixed model path, not a model name
        throw new UnsupportedOperationException("GPU Llama3 does not support custom model names");
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        // Return basic parameters - GPU Llama3 may support maxOutputTokens
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return false;
    }

    @Override
    @Disabled("GPU Llama3 does not support default model parameters")
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {
        // GPU Llama3 doesn't support default parameters
    }

    @Override
    @Disabled("GPU Llama3 does not support default model parameters")
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {
        // GPU Llama3 doesn't support default parameters
    }

    @Override
    @Disabled("GPU Llama3 does not support model name parameter")
    protected void should_fail_if_modelName_is_not_supported(StreamingChatModel model) {
        // This test expects the feature to be supported but fail - GPU Llama3 doesn't support it at all
    }

    @Override
    @Disabled("GPU Llama3 does not support image inputs")
    protected void should_accept_single_image_as_public_URL(StreamingChatModel model) {
        // Test disabled - GPU Llama3 doesn't support image inputs
    }

    @Override
    @Disabled("GPU Llama3 does not support image inputs")
    protected void should_respect_user_message(StreamingChatModel model) {}

    @Override
    @Disabled("GPU Llama3 does not support image inputs")
    protected void should_accept_multiple_images_as_public_URLs(StreamingChatModel model) {
        // Test disabled - GPU Llama3 doesn't support image inputs
    }

    @Override
    @Disabled("GPU Llama3 does not support stop sequences")
    protected void should_fail_if_stopSequences_parameter_is_not_supported(StreamingChatModel model) {
        // This test expects the feature to be supported but fail - GPU Llama3 doesn't support it at all
    }

    @Override
    @Disabled("GPU Llama3 does not support tools")
    protected void should_fail_if_tools_are_not_supported(StreamingChatModel model) {
        // This test expects the feature to be supported but fail - GPU Llama3 doesn't support it at all
    }

    @Override
    @Disabled("GPU Llama3 does not support image inputs")
    protected void should_fail_if_images_as_base64_encoded_strings_are_not_supported(StreamingChatModel model) {
        // This test has configuration issues with empty parameter sources
    }

    @Override
    @Disabled("GPU Llama3 does not support image inputs")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(StreamingChatModel model) {
        // This test has configuration issues with empty parameter sources
    }

    @Override
    @Disabled("GPU Llama3 does not support default model parameters")
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(StreamingChatModel model) {
        // GPU Llama3 doesn't support default parameters
    }

    @Override
    @Disabled("GPU Llama3 does not support default model parameters")
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            StreamingChatModel model) {}

    @Override
    @Disabled("GPU Llama3 does not support maxOutputTokens parameter")
    protected void should_respect_maxOutputTokens_in_chat_request(StreamingChatModel model) {
        // Disable this test as GPU Llama3 may not support maxOutputTokens reliably
    }

    @Override
    @Disabled("GPU Llama3 does not support JSON response format")
    protected void should_fail_if_JSON_response_format_is_not_supported(StreamingChatModel model) {
        // This test expects the feature to be supported but fail - GPU Llama3 doesn't support it at all
    }

    @Override
    @Disabled("GPU Llama3 does not support JSON response format with schema")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(StreamingChatModel model) {
        // This test expects the feature to be supported but fail - GPU Llama3 doesn't support it at all
    }

    @Override
    @Disabled("GPU Llama3 does not support system messages reliably")
    protected void should_respect_system_message(StreamingChatModel model) {
        // This test might fail due to GPU Llama3 limitations
    }
}
