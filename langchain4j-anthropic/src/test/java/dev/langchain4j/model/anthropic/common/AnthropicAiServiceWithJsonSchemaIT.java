package dev.langchain4j.model.anthropic.common;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModelName;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                AnthropicChatModel.builder()
                        .baseUrl(System.getenv("ANTHROPIC_CACHING_BASE_URL"))
                        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                        .modelName(AnthropicChatModelName.CLAUDE_SONNET_4_6)
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .logRequests(false)
                        .logRequests(true)
                        .build(),
                AnthropicChatModel.builder()
                        .baseUrl(System.getenv("ANTHROPIC_CACHING_BASE_URL"))
                        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                        .beta("structured-outputs-2025-11-13") // testing backward compatibility
                        .modelName(AnthropicChatModelName.CLAUDE_SONNET_4_6)
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .logRequests(false)
                        .logRequests(true)
                        .build()
        );
    }
}
