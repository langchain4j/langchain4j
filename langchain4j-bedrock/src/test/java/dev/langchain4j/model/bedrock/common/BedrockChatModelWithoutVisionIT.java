package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static dev.langchain4j.model.bedrock.TestedModels.AWS_NOVA_MICRO;
import static dev.langchain4j.model.bedrock.TestedModels.COHERE_COMMAND_R_PLUS;
import static dev.langchain4j.model.bedrock.TestedModels.MISTRAL_LARGE;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelWithoutVisionIT extends AbstractChatModelIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(AWS_NOVA_MICRO, COHERE_COMMAND_R_PLUS, MISTRAL_LARGE);
    }

    @Override
    protected List<ChatModel> modelsSupportingTools() {
        return List.of(AWS_NOVA_MICRO, COHERE_COMMAND_R_PLUS, MISTRAL_LARGE);
    }

    @Override
    protected String customModelName() {
        return "cohere.command-r-v1:0";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return BedrockChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return BedrockChatModel.builder()
                .defaultRequestParameters(parameters)
                // force a working model with stopSequence parameter for @Tests
                .modelId("cohere.command-r-v1:0")
                .build();
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
        // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // output format not supported
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // output format not supported
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

    // OVERRIDED TESTS

    // Nova models include support StopSequence but have an incoherrent behavior, it includes the stopSequence in the
    // response
    // TODO Titan express error : "Malformed input request: 3 schema violations found"
    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(ChatModel model) {
        if (!model.equals(AWS_NOVA_MICRO)) {
            super.should_respect_system_message(model);
        }
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

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
