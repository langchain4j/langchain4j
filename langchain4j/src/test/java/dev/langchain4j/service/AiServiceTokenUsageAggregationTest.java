package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiServiceTokenUsageAggregationTest {

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    static class Tools {

        @Tool
        String currentTemperature() {
            return "42";
        }
    }

    /**
     * Mimics a provider {@link TokenUsage} subclass that only narrows the type and therefore
     * has no reason to override {@link TokenUsage#add(TokenUsage)}.
     */
    static class VendorTokenUsage extends TokenUsage {

        VendorTokenUsage(Integer inputTokenCount, Integer outputTokenCount) {
            super(inputTokenCount, outputTokenCount);
        }
    }

    @Test
    void should_aggregate_token_usage_across_tool_calling_round_trips() {

        AtomicInteger modelCalls = new AtomicInteger();

        ChatModel chatModel = new ChatModel() {

            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                if (modelCalls.getAndIncrement() == 0) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                    .id("1")
                                    .name("currentTemperature")
                                    .arguments("{}")
                                    .build()))
                            .metadata(ChatResponseMetadata.builder()
                                    .tokenUsage(new VendorTokenUsage(10, 5))
                                    .build())
                            .build();
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("It is 42 degrees"))
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(new VendorTokenUsage(20, 7))
                                .build())
                        .build();
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new Tools())
                .build();

        Result<String> result = assistant.chat("What is the temperature?");

        assertThat(result.content()).isEqualTo("It is 42 degrees");
        assertThat(modelCalls).hasValue(2);
        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(30);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(12);
        assertThat(result.tokenUsage().totalTokenCount()).isEqualTo(42);
    }
}
