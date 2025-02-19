package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.chat.common.ChatModelCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.chat.common.ChatLanguageModelCapabilities;

public final class TestedModelsWithConverseAPI {

    // JsonResponseFormat and JsonResponseFormatWithSchema are not yet supported by Bedrock ConverseAPI

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html

    // AWS_NOVA_MICRO doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    public static final ChatLanguageModelCapabilities AWS_NOVA_MICRO = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("us.amazon.nova-micro-v1:0")
                    .build())
            .mnemonicName("amazon nova micro")
            .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
            .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
            .supportsToolChoiceRequired(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities AWS_NOVA_LITE = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("us.amazon.nova-lite-v1:0")
                    .build())
            .mnemonicName("amazon nova lite")
            .supportsToolChoiceRequired(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities AWS_NOVA_PRO = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder().modelId("us.amazon.nova-pro-v1:0").build())
            .mnemonicName("amazon nova pro")
            .supportsToolChoiceRequired(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
    public static final ChatLanguageModelCapabilities CLAUDE_3_HAIKU = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("anthropic.claude-3-haiku-20240307-v1:0")
                    .build())
            .mnemonicName("claude 3 haiku")
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    // ToolChoice "only supported by Anthropic Claude 3 models and by Mistral AI Mistral Large" from
    // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ToolChoice.html
    // MISTRAL_LARGE doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    public static final ChatLanguageModelCapabilities MISTRAL_LARGE = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("mistral.mistral-large-2402-v1:0")
                    .build())
            .mnemonicName("mistral large")
            .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
            .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities LLAMA_3_2_90B = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("meta.llama3-2-90b-instruct-v1:0")
                    .build())
            .mnemonicName("llama 3.2 90b instruct")
            .supportsToolChoiceRequired(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    // COHERE_COMMAND_R_PLUS doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    public static final ChatLanguageModelCapabilities COHERE_COMMAND_R_PLUS = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("cohere.command-r-plus-v1:0")
                    .build())
            .mnemonicName("cohere command r plus")
            .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
            .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
            .supportsToolChoiceRequired(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    // AWS_NOVA_MICRO, COHERE_COMMAND_R_PLUS, AI_JAMBA_1_5_MINI, MISTRAL_LARGE doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    public static final ChatLanguageModelCapabilities AI_JAMBA_1_5_MINI = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("ai21.jamba-1-5-mini-v1:0")
                    .build())
            .mnemonicName("ai21 jamba 1.5 mini")
            .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
            .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
            .supportsToolChoiceRequired(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();
}
