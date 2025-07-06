package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public final class TestedModels {

    public static final ChatModel AWS_NOVA_MICRO =
            BedrockChatModel.builder().modelId("us.amazon.nova-micro-v1:0").build();
    public static final StreamingChatModel STREAMING_AWS_NOVA_MICRO = BedrockStreamingChatModel.builder()
            .modelId("us.amazon.nova-micro-v1:0")
            .build();

    public static final ChatModel AWS_NOVA_LITE =
            BedrockChatModel.builder().modelId("us.amazon.nova-lite-v1:0").build();
    public static final StreamingChatModel STREAMING_AWS_NOVA_LITE = BedrockStreamingChatModel.builder()
            .modelId("us.amazon.nova-lite-v1:0")
            .build();

    public static final ChatModel AWS_NOVA_PRO =
            BedrockChatModel.builder().modelId("us.amazon.nova-pro-v1:0").build();
    public static final StreamingChatModel STREAMING_AWS_NOVA_PRO = BedrockStreamingChatModel.builder()
            .modelId("us.amazon.nova-pro-v1:0")
            .build();

    public static final ChatModel CLAUDE_3_HAIKU = BedrockChatModel.builder()
            .modelId("anthropic.claude-3-haiku-20240307-v1:0")
            .build();
    public static final StreamingChatModel STREAMING_CLAUDE_3_HAIKU = BedrockStreamingChatModel.builder()
            .modelId("anthropic.claude-3-haiku-20240307-v1:0")
            .build();

    public static final ChatModel MISTRAL_LARGE = BedrockChatModel.builder()
            .modelId("mistral.mistral-large-2402-v1:0")
            .build();
    public static final StreamingChatModel STREAMING_MISTRAL_LARGE = BedrockStreamingChatModel.builder()
            .modelId("mistral.mistral-large-2402-v1:0")
            .build();

    public static final ChatModel LLAMA_3_2_90B = BedrockChatModel.builder()
            .modelId("meta.llama3-2-90b-instruct-v1:0")
            .build();

    public static final ChatModel COHERE_COMMAND_R_PLUS =
            BedrockChatModel.builder().modelId("cohere.command-r-plus-v1:0").build();
    public static final StreamingChatModel STREAMING_COHERE_COMMAND_R_PLUS = BedrockStreamingChatModel.builder()
            .modelId("cohere.command-r-plus-v1:0")
            .build();

    public static final ChatModel AI_JAMBA_1_5_MINI =
            BedrockChatModel.builder().modelId("ai21.jamba-1-5-mini-v1:0").build();
    public static final StreamingChatModel STREAMING_AI_JAMBA_1_5_MINI = BedrockStreamingChatModel.builder()
            .modelId("ai21.jamba-1-5-mini-v1:0")
            .build();
}
