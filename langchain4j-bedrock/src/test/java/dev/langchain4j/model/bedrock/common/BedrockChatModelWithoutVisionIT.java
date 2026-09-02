package dev.langchain4j.model.bedrock.common;

import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_MICRO;
import static dev.langchain4j.model.bedrock.TestedModels.MISTRAL_LARGE;
import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.bedrock.BedrockChatResponseMetadata;
import dev.langchain4j.model.bedrock.BedrockTokenUsage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelWithoutVisionIT extends AbstractChatModelIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(AWS_NOVA_MICRO, MISTRAL_LARGE);
    }

    @Override
    protected List<ChatModel> modelsSupportingTools() {
        return List.of(AWS_NOVA_MICRO, MISTRAL_LARGE);
    }

    @Override
    protected String customModelName() {
        return "us.amazon.nova-lite-v1:0";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return BedrockChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        BedrockChatModel.Builder builder = BedrockChatModel.builder().defaultRequestParameters(parameters);
        if (parameters.modelName() == null) {
            // Claude excludes the stop sequence from the response, as should_respect_stopSequences_* expects
            builder.modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0");
        }
        return builder.build();
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel model) {
        return BedrockTokenUsage.class;
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
        // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // JSON response format without schema is not supported
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // not supported for models used in this class
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false; // not supported for models used in this class
    }

    @Override
    protected boolean assertExceptionType() {
        // Bedrock throws ValidationException, while test expects UnsupportedFeatureException
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        // These models doesn't support image as input parameters
        // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        // These models doesn't support image as input parameters
        // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
        return false;
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(final ChatModel model) {
        return BedrockChatResponseMetadata.class;
    }

    // OVERRIDED TESTS

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(ChatModel model) {
        if (model.equals(AWS_NOVA_MICRO)) {
            return; // Nova models support stopSequences, but include the stop sequence in the response
        }
        super.should_respect_stopSequences_in_chat_request(model);
    }

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @DisabledIf("supportsToolChoiceRequired")
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(ChatModel model) {
        if (List.of(MISTRAL_LARGE, AWS_NOVA_MICRO).contains(model)) {
            super.should_force_LLM_to_execute_any_tool(model);
        } else {
            super.should_fail_if_tool_choice_REQUIRED_is_not_supported(model);
        }
    }

    @Disabled("Sorry but I can't tell you that information because is not appropriate to share someone's personal information")
    @Override
    protected void should_respect_multiple_messages(ChatModel model) {
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
