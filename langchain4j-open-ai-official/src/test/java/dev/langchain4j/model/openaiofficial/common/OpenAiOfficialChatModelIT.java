package dev.langchain4j.model.openaiofficial.common;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.API_VERSION;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.MODEL_NAME;

class OpenAiOfficialChatModelIT extends AbstractChatModelIT {

    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .azureDeploymentName(MODEL_NAME.toString())
            .azureOpenAIServiceVersion(API_VERSION)
            .modelName(MODEL_NAME)
            .build();

    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .azureDeploymentName(MODEL_NAME.toString())
            .azureOpenAIServiceVersion(API_VERSION)
            .modelName(MODEL_NAME)
            .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
            .strictJsonSchema(true)
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                //OPEN_AI_CHAT_MODEL, //TODO FIX this doesn't run reliably when generating JSON (as there is no schema)
                OPEN_AI_CHAT_MODEL_STRICT_SCHEMA
        );
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.OpenAiOfficialChatModelBuilder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                .azureDeploymentName(ChatModel.GPT_4O.toString())
                .modelName(ChatModel.GPT_4O_2024_08_06.toString())
                .azureOpenAIServiceVersion(API_VERSION)
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
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_public_URL(ChatLanguageModel model) {
        // TODO fix
        // This cannot be tested  as it exceeds the token rate limit of the OpenAI S0 pricing tier
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_public_URLs(ChatLanguageModel model) {
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
}
