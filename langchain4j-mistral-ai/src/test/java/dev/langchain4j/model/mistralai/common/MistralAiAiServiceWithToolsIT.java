package dev.langchain4j.model.mistralai.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static java.util.Collections.singletonList;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiAiServiceWithToolsIT extends AbstractAiServiceWithToolsIT {

    private static final String ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER =
            " Always use available tools to calculate the answer.";

    @Override
    protected List<ChatModel> models() {
        return singletonList(MistralAiChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName("mistral-small-2506")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    @Override
    protected boolean verifyModelInteractions() {
        return true;
    }

    @Override
    @Disabled("Mistral is too strict and expects assistant message after tool message")
    protected void should_keep_memory_consistent_using_return_immediate(ChatModel model) {
    }

    @Override
    protected String adaptPrompt1(String prompt) {
        return prompt + ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER;
    }

    @Override
    protected String adaptPrompt2(String prompt) {
        return prompt + ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER;
    }

    @Override
    protected String adaptPrompt3(String prompt) {
        return prompt + ALWAYS_USE_AVAILABLE_TOOLS_TO_CALCULATE_THE_ANSWER;
    }
}
