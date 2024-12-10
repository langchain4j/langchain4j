package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithNewToolsIT;

import java.util.List;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static java.util.Collections.singletonList;

class AnthropicAiServicesWithToolsIT extends AiServicesWithNewToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(
                AnthropicChatModel.builder()
                        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                        .modelName(CLAUDE_3_5_HAIKU_20241022)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }
}
