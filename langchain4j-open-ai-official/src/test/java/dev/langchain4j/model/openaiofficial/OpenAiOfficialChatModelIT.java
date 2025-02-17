package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialTestHelper.MODEL_NAME;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class OpenAiOfficialChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalOpenAiOfficialTestHelper.modelsNormalAndJsonStrict();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.Builder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
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
        // This cannot be tested  as it exceeds the token rate limit of the OpenAI S0 pricing tier
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
