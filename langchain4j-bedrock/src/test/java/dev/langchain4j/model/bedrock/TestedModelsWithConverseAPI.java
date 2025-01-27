package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatLanguageModel;

public final class TestedModelsWithConverseAPI {

    public static final ChatLanguageModel AWS_NOVA_MICRO =
            BedrockChatModel.builder().modelId("us.amazon.nova-micro-v1:0").build();

    public static final ChatLanguageModel AWS_NOVA_LITE =
            BedrockChatModel.builder().modelId("us.amazon.nova-lite-v1:0").build();

    public static final ChatLanguageModel AWS_NOVA_PRO =
            BedrockChatModel.builder().modelId("us.amazon.nova-pro-v1:0").build();

    public static final ChatLanguageModel AWS_TITAN_TEXT_EXPRESS =
            BedrockChatModel.builder().modelId("amazon.titan-text-express-v1").build();

    public static final ChatLanguageModel CLAUDE_3_HAIKU = BedrockChatModel.builder()
            .modelId("anthropic.claude-3-haiku-20240307-v1:0")
            .build();

    public static final ChatLanguageModel MISTRAL_LARGE = BedrockChatModel.builder()
            .modelId("mistral.mistral-large-2402-v1:0")
            .build();

    public static final ChatLanguageModel LLAMA_3_2_90B = BedrockChatModel.builder()
            .modelId("meta.llama3-2-90b-instruct-v1:0")
            .build();

    public static final ChatLanguageModel COHERE_COMMAND_R_PLUS =
            BedrockChatModel.builder().modelId("cohere.command-r-plus-v1:0").build();

    public static final ChatLanguageModel AI_JAMBA_INSTRUCT =
            BedrockChatModel.builder().modelId("ai21.jamba-instruct-v1:0").build();
}
