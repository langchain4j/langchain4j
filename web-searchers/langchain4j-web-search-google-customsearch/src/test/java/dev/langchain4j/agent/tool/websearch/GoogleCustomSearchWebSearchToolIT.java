package dev.langchain4j.agent.tool.websearch;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCustomSearchWebSearchToolIT {

    ChatLanguageModel model = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .logRequests(false)
            .build();

    interface Assistant {
        @SystemMessage({
                "You are a web search support agent.",
                "If there is any event that has not happened yet",
                "You MUST create a web search request with with user query and",
                "use the web search tool to search the web for organic web results.",
                "Include the source link in your final response."
        })
        String chat(String userMessage);
    }

    @Test
    void should_execute_tool() {
        // given
        GoogleCustomWebSearchTool webTool = GoogleCustomWebSearchTool.withApiKeyAndCsi(
                System.getenv("GOOGLE_API_KEY"),
                System.getenv("GOOGLE_SEARCH_ENGINE_ID"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .tools(webTool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        // when
        String answer = assistant.chat("Search in the web who won the FIFA World Cup 2022?");

        // then
        assertThat(answer).containsIgnoringCase("Argentina");
    }

}
