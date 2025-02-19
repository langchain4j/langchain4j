package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.common.ChatLanguageModelCapabilities;

public final class TestedModelsWithConverseAPI {

    public static final ChatLanguageModelCapabilities AWS_NOVA_MICRO = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("us.amazon.nova-micro-v1:0")
                    .build())
            .supportsSingleImageInputAsPublicURL(false)
            .supportsSingleImageInputAsBase64EncodedString(false)
            .supportsToolChoiceRequired(false)
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities AWS_NOVA_LITE = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("us.amazon.nova-lite-v1:0")
                    .build())
            .supportsToolChoiceRequired(false)
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities AWS_NOVA_PRO = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder().modelId("us.amazon.nova-pro-v1:0").build())
            .supportsToolChoiceRequired(false)
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities CLAUDE_3_HAIKU = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("anthropic.claude-3-haiku-20240307-v1:0")
                    .build())
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities MISTRAL_LARGE = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("mistral.mistral-large-2402-v1:0")
                    .build())
            .supportsSingleImageInputAsPublicURL(false)
            .supportsSingleImageInputAsBase64EncodedString(false)
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities LLAMA_3_2_90B = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("meta.llama3-2-90b-instruct-v1:0")
                    .build())
            .supportsToolChoiceRequired(false)
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities COHERE_COMMAND_R_PLUS = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("cohere.command-r-plus-v1:0")
                    .build())
            .supportsSingleImageInputAsPublicURL(false)
            .supportsSingleImageInputAsBase64EncodedString(false)
            .supportsToolChoiceRequired(false)
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();

    public static final ChatLanguageModelCapabilities AI_JAMBA_1_5_MINI = ChatLanguageModelCapabilities.builder()
            .model(BedrockChatModel.builder()
                    .modelId("ai21.jamba-1-5-mini-v1:0")
                    .build())
            .supportsSingleImageInputAsPublicURL(false)
            .supportsSingleImageInputAsBase64EncodedString(false)
            .supportsToolChoiceRequired(false)
            .supportsJsonResponseFormat(false)
            .supportsJsonResponseFormatWithSchema(false)
            .assertExceptionType(false)
            .build();
}
