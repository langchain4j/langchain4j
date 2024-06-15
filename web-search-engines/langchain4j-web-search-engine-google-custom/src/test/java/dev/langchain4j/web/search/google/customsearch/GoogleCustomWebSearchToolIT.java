package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.WebSearchToolIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_SEARCH_ENGINE_ID", matches = ".*")
class GoogleCustomWebSearchToolIT extends WebSearchToolIT {

    WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.withApiKeyAndCsi(
            System.getenv("GOOGLE_API_KEY"),
            System.getenv("GOOGLE_SEARCH_ENGINE_ID"));

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(OpenAiChatModelName.GPT_3_5_TURBO)
            .logRequests(true)
            .build();

    interface Assistant {
        @dev.langchain4j.service.SystemMessage({
                "You are a web search support agent.",
                "If there is any event that has not happened yet",
                "You MUST create a web search request with with user query and",
                "use the web search tool to search the web for organic web results.",
                "Include the source link in your final response."
        })
        String answer(String userMessage);
    }

    @Test
    void should_execute_google_tool_with_chatLanguageModel_to_give_a_final_response(){
        // given
        googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequests(true)
                .logResponses(true)
                .maxRetries(3)
                .build();

        WebSearchTool webSearchTool = WebSearchTool.from(googleSearchEngine);
        List<ToolSpecification> tools = ToolSpecifications.toolSpecificationsFrom(webSearchTool);
        String query = "What are the release dates for the movies coming out last week of May 2024?";
        List<ChatMessage> messages = new ArrayList<>();
        SystemMessage systemMessage = SystemMessage.from("You are a web search support agent. If there is any event that has not happened yet, you MUST use a web search tool to look up the information on the web. Include the source link in your final response. Do not say that you have not the capability to browse the web in real time");
        messages.add(systemMessage);
        UserMessage userMessage = UserMessage.from(query);
        messages.add(userMessage);
        // when
        AiMessage aiMessage = chatLanguageModel().generate(messages, tools).content();

        // then
        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        assertThat(aiMessage.toolExecutionRequests())
                .anySatisfy(toolSpec -> {
                            assertThat(toolSpec.name())
                                    .containsIgnoringCase("searchWeb");
                            assertThat(toolSpec.arguments())
                                    .isNotBlank();
                        }
                );
        messages.add(aiMessage);

        // when
        String strResult = webSearchTool.searchWeb(query);
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(aiMessage.toolExecutionRequests().get(0), strResult);
        messages.add(toolExecutionResultMessage);

        AiMessage finalResponse = chatLanguageModel().generate(messages).content();
        System.out.println(finalResponse.text());

        // then
        assertThat(finalResponse.text())
                .as("At least the string result should be contains 'movies' and 'coming soon' ignoring case")
                .containsIgnoringCase("movies")
                .containsIgnoringCase("May 2024");
    }

    @Test
    void should_execute_google_tool_with_chatLanguageModel_to_summary_response_in_images() {
        // given
        googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequests(true)
                .logResponses(true)
                .build();

        WebSearchTool webSearchTool = WebSearchTool.from(googleSearchEngine);
        List<ToolSpecification> tools = ToolSpecifications.toolSpecificationsFrom(webSearchTool);
        String query = "My family is coming to visit me in Madrid next week, list the best tourist activities suitable for the whole family";
        List<ChatMessage> messages = new ArrayList<>();
        SystemMessage systemMessage = SystemMessage.from("You are a web search support agent. If there is any event that has not happened yet, you MUST use a web search tool to look up the information on the web. Include the source link in your final response and the image urls. Do not say that you have not the capability to browse the web in real time");
        messages.add(systemMessage);
        UserMessage userMessage = UserMessage.from(query);
        messages.add(userMessage);
        // when
        AiMessage aiMessage = chatLanguageModel().generate(messages, tools).content();

        // then
        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        assertThat(aiMessage.toolExecutionRequests())
                .anySatisfy(toolSpec -> {
                            assertThat(toolSpec.name())
                                    .containsIgnoringCase("searchWeb");
                            assertThat(toolSpec.arguments())
                                    .isNotBlank();
                        }
                );
        messages.add(aiMessage);

        // when
        String strResult = webSearchTool.searchWeb(query);
        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(aiMessage.toolExecutionRequests().get(0), strResult);
        messages.add(toolExecutionResultMessage);

        AiMessage finalResponse = chatLanguageModel().generate(messages).content();
        System.out.println(finalResponse.text());

        // then
        assertThat(finalResponse.text())
                .as("At least the string result should be contains 'madrid' and 'tourist' ignoring case")
                .containsIgnoringCase("Madrid")
                .containsIgnoringCase("Royal Palace");
    }

    @Test
    void should_execute_google_tool_with_AiServices() {
        // given
        WebSearchTool webTool = WebSearchTool.from(googleSearchEngine);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .tools(webTool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
        // when
        String answer = assistant.answer("Search in the web who won the FIFA World Cup 2022?");

        // then
        assertThat(answer).containsIgnoringCase("Argentina");
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return googleSearchEngine;
    }

    @Override
    protected ChatLanguageModel chatLanguageModel() {
        return chatModel;
    }
}
