package dev.langchain4j.model.openaiofficial.microsoftfoundry;

import static dev.langchain4j.model.openaiofficial.microsoftfoundry.InternalMicrosoftFoundryTestHelper.CHAT_MODEL_NAME_ALTERNATE;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "MICROSOFT_FOUNDRY_API_KEY", matches = ".+")
class MicrosoftFoundryChatModelIT extends AbstractChatModelIT {

    @Override
    protected List<ChatModel> models() {
        return InternalMicrosoftFoundryTestHelper.chatModelsNormalAndJsonStrict();
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.Builder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("MICROSOFT_FOUNDRY_ENDPOINT"))
                .apiKey(System.getenv("MICROSOFT_FOUNDRY_API_KEY"))
                .microsoftFoundryDeploymentName(CHAT_MODEL_NAME_ALTERNATE.asString())
                .defaultRequestParameters(parameters);

        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE);
        }
        return openAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "gpt-5.1-custom";
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

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel chatModel) {
        return OpenAiOfficialChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return OpenAiOfficialTokenUsage.class;
    }

    @Disabled(
            "TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Microsoft Foundry API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsPublicURL")
    protected void should_accept_single_image_as_public_URL(ChatModel model) {}

    @Disabled(
            "TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Microsoft Foundry API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsBase64EncodedString")
    protected void should_accept_single_image_as_base64_encoded_string(ChatModel model) {}

    @Disabled(
            "TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Microsoft Foundry API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsMultipleImageInputsAsPublicURLs")
    protected void should_accept_multiple_images_as_public_URLs(ChatModel model) {}

    @Disabled(
            "TODO fix: com.openai.errors.RateLimitException: 429: Requests to the ChatCompletions_Create Operation under Microsoft Foundry API version 2024-10-21 have exceeded token rate limit of your current OpenAI S0 pricing tier.")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsMultipleImageInputsAsBase64EncodedStrings")
    protected void should_accept_multiple_images_as_base64_encoded_strings(ChatModel model) {}
}
