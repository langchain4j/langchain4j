package dev.langchain4j.model.openaiofficial.azureopenai;

import static dev.langchain4j.model.openaiofficial.azureopenai.InternalAzureOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiOfficialChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return InternalAzureOpenAiOfficialTestHelper.chatModelsNormalAndJsonStrict();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.Builder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .azureDeploymentName(ChatModel.GPT_4O.toString())
                .defaultRequestParameters(parameters);

        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE);
        }
        return openAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false;
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Disabled("TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Azure OpenAI API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsPublicURL")
    protected void should_accept_single_image_as_public_URL(ChatLanguageModel model) {
    }

    @Disabled("TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Azure OpenAI API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsBase64EncodedString")
    protected void should_accept_single_image_as_base64_encoded_string(ChatLanguageModel model) {
    }

    @Disabled("TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Azure OpenAI API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsMultipleImageInputsAsPublicURLs")
    protected void should_accept_multiple_images_as_public_URLs(ChatLanguageModel model) {
    }

    @Disabled("TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Azure OpenAI API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsMultipleImageInputsAsBase64EncodedStrings")
    protected void should_accept_multiple_images_as_base64_encoded_strings(ChatLanguageModel model) {
    }
}
