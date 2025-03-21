package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.chat.common.ChatModelAndCapabilities;

public final class TestedModelsWithConverseAPI {

    // JsonResponseFormat and JsonResponseFormatWithSchema are not yet supported by Bedrock ConverseAPI

    // AWS_NOVA_MICRO doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    public static final ChatModelAndCapabilities AWS_NOVA_MICRO = ChatModelAndCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("us.amazon.nova-micro-v1:0")
                    .build())
            .mnemonicName("amazon nova micro")
            .supportsSingleImageInputAsPublicURL(NOT_SUPPORTED)
            .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    public static final ChatModelAndCapabilities AWS_NOVA_LITE = ChatModelAndCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("us.amazon.nova-lite-v1:0")
                    .build())
            .mnemonicName("amazon nova lite")
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    public static final ChatModelAndCapabilities AWS_NOVA_PRO = ChatModelAndCapabilities.builder()
            .model(BedrockChatModel.builder().modelId("us.amazon.nova-pro-v1:0").build())
            .mnemonicName("amazon nova pro")
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    public static final ChatModelAndCapabilities CLAUDE_3_HAIKU = ChatModelAndCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("anthropic.claude-3-haiku-20240307-v1:0")
                    .build())
            .mnemonicName("claude 3 haiku")
            .supportsJsonResponseFormat(NOT_SUPPORTED)
            .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
            .assertExceptionType(false)
            .build();

    // MISTRAL_LARGE doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    public static final ChatModelAndCapabilities MISTRAL_LARGE = ChatModelAndCapabilities.builder()
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

    public static final ChatModelAndCapabilities LLAMA_3_2_90B = ChatModelAndCapabilities.builder()
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
    // COHERE_COMMAND_R_PLUS doesn't support required tool
    public static final ChatModelAndCapabilities COHERE_COMMAND_R_PLUS = ChatModelAndCapabilities.builder()
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

    // AI_JAMBA_1_5_MINI doesn't support image as input parameters
    // https://docs.aws.amazon.com/bedrock/latest/userguide/models-supported.html
    // AI_JAMBA_1_5_MINI doesn't support required tool
    public static final ChatModelAndCapabilities AI_JAMBA_1_5_MINI = ChatModelAndCapabilities.builder()
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
