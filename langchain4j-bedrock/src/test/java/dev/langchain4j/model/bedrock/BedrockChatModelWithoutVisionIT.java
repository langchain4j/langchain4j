package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AI_JAMBA_INSTRUCT;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_NOVA_MICRO;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.AWS_TITAN_TEXT_EXPRESS;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.COHERE_COMMAND_R_PLUS;
import static dev.langchain4j.model.bedrock.TestedModelsWithConverseAPI.MISTRAL_LARGE;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockChatModelWithoutVisionIT extends AbstractChatModelIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(AWS_NOVA_MICRO, COHERE_COMMAND_R_PLUS, AI_JAMBA_INSTRUCT, MISTRAL_LARGE, AWS_TITAN_TEXT_EXPRESS);
    }

    @Override
    protected List<ChatLanguageModel> modelsSupportingTools() {
        return List.of(AWS_NOVA_MICRO, COHERE_COMMAND_R_PLUS, MISTRAL_LARGE);
    }

    @Override
    protected String customModelName() {
        return "cohere.command-r-v1:0";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        return BedrockChatModel.builder()
                .defaultRequestParameters(parameters)
                // force a working model with stopSequence parameter for @Tests
                .modelId("cohere.command-r-v1:0")
                .build();
    }

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
    @Override
    protected boolean supportsToolChoiceRequired() {
        return false;
    }

    // output format not supported
    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    // output format not supported
    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean assertExceptionType() {
        return false;
    }

    // These models doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false;
    }

    // OVERRIDED TESTS

    // TITAN_EXPRESS doesn't support system prompt
    // https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference-supported-models-features.html
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_respect_system_message(ChatLanguageModel model) {
        if (!model.equals(AWS_TITAN_TEXT_EXPRESS)) {
            super.should_respect_system_message(model);
        }
    }

    // Nova models include support StopSequence but have an incoherrent behavior, it includes the stopSequence in the
    // response
    // TODO Titan express error : "Malformed input request: 3 schema violations found"
    @Override
    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(ChatLanguageModel model) {
        if (!model.equals(AWS_TITAN_TEXT_EXPRESS) && !model.equals(AWS_NOVA_MICRO)) {
            super.should_respect_system_message(model);
        }
    }

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @DisabledIf("supportsToolChoiceRequired")
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(ChatLanguageModel model) {
        if (model.equals(MISTRAL_LARGE)) {
            super.should_force_LLM_to_execute_any_tool(model);
        } else {
            super.should_fail_if_tool_choice_REQUIRED_is_not_supported(model);
        }
    }
}
