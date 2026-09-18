package dev.langchain4j.skills;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SkillsStreamingMemoryEvictionTest {

    interface Assistant {
        TokenStream chat(String userMessage);
    }

    static class InventoryTools {

        @Tool("Queries the internal inventory system for stock levels")
        String query_inventory() {
            return "47 units in stock";
        }
    }

    @Test
    void should_complete_when_user_message_evicted_from_memory_window_during_tool_loop() throws Exception {

        Skill skill = Skill.builder()
                .name("inventory-management")
                .description("Describes how to query and manage the internal inventory system")
                .content("When asked about inventory or stock levels, use the 'query_inventory' tool.")
                .tools(new InventoryTools())
                .build();

        Skills skills = Skills.from(skill);

        StreamingChatModelMock streamingChatModelMock = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("activate_skill")
                        .arguments("{\"skill_name\":\"inventory-management\"}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("query_inventory")
                        .arguments("{}")
                        .build()),
                AiMessage.from("There are 47 units in stock."));

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingChatModelMock)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(3))
                .systemMessage("You have access to the following skills:\n" + skills.formatAvailableSkills()
                        + "\nActivate the relevant skill first using the 'activate_skill' tool.")
                .toolProvider(skills.toolProvider())
                .build();

        ChatResponse response = chat(assistant, "Check the inventory for widgets");

        assertThat(response.aiMessage().text()).contains("47 units");
    }

    private static ChatResponse chat(Assistant assistant, String userMessage) throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat(userMessage)
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        return future.get(60, SECONDS);
    }
}
