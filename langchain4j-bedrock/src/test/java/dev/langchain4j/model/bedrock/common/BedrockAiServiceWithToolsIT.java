package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.bedrock.TestedModels.MISTRAL_LARGE;
import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(MISTRAL_LARGE);
    }

    @Override
    @Disabled("Bedrock is too strict and expects assistant message after tool message")
    protected void should_keep_memory_consistent_using_return_immediate(ChatModel model) {}

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
