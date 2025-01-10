package dev.langchain4j.model.bedrock.converse;

import dev.langchain4j.model.chat.ChatLanguageModel;

public final class TestedModels {

    public static final ChatLanguageModel AWS_NOVA_MICRO = BedrockChatModel.builder()
            .modelId("us.amazon.nova-micro-v1:0")
            .temperature(0.1f)
            .build();

    public static final ChatLanguageModel AWS_NOVA_LITE = BedrockChatModel.builder()
            .modelId("us.amazon.nova-micro-v1:0")
            .temperature(0.1f)
            .build();
}
