package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.MODEL_NAME;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OpenAiOfficialStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final OpenAiOfficialStreamingChatModel OPEN_AI_OFFICIAL_STREAMING_CHAT_MODEL =
            OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .build();

    static final OpenAiOfficialStreamingChatModel OPEN_AI_OFFICIAL_STREAMING_CHAT_MODEL_STRICT_SCHEMA =
            OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                // OPEN_AI_OFFICIAL_STREAMING_CHAT_MODEL, //TODO FIX this doesn't run reliably when generating JSON (as
                // there is no schema)
                OPEN_AI_OFFICIAL_STREAMING_CHAT_MODEL_STRICT_SCHEMA
                // TODO Add a model using OpenAI (NOT Azure OpenAI)
                );
    }

    @Override
    protected StreamingChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialStreamingChatModel.OpenAiOfficialStreamingChatModelBuilder openAiChatModelBuilder =
                OpenAiOfficialStreamingChatModel.builder()
                        .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .modelName(MODEL_NAME)
                        .maxCompletionTokens(parameters.maxOutputTokens())
                        .defaultRequestParameters(parameters);

        return openAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_08_06.toString();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean supportsModelNameParameter() {
        // With Azure OpenAI, the deployment name is part of the URL, changing the model name will not have any effect.
        return false;
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_public_URL(StreamingChatLanguageModel model) {
        // TODO fix
        // This cannot be tested  as it exceeds the token rate limit of the OpenAI S0 pricing tier
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_public_URLs(StreamingChatLanguageModel model) {
        // TODO fix
        // This cannot be tested  as it exceeds the token rate limit of the OpenAI S0 pricing tier
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        // TODO fix
        // gpt-4o-mini does not support base64-encoded images
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        // TODO fix
        // The issue is in test should_force_LLM_to_execute_any_tool
        // When executing a required tool,
        // OpenAI returns a finish reason of "STOP" and not "TOOL_EXECUTION" as expected
        return false;
    }

    @Override
    protected boolean assertThreads() {
        // TODO why would it run in another thread?
        return false;
    }
}
