package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetrieverIT;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.web.search.WebSearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_SEARCH_ENGINE_ID", matches = ".*")
class GoogleCustomWebSearchContentRetrieverIT extends WebSearchContentRetrieverIT {

    WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.withApiKeyAndCsi(
            System.getenv("GOOGLE_API_KEY"),
            System.getenv("GOOGLE_SEARCH_ENGINE_ID"));

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(OpenAiChatModelName.GPT_3_5_TURBO)
            .logRequests(true)
            .build();

    interface Assistant {
        @SystemMessage({
                "You are a web search support agent.",
                "If there is any event that has not happened yet, ",
                "you MUST use a web search tool to look up the information on the web.",
                "Include the source link and the image urls in your final response if these known, otherwise, do not include them.",
                "Do not say that you have not the capability to browse the web in real time"
        })
        String answer(String userMessage);
    }

    @Test
    void should_retrieve_web_content_with_google_for_current_info() {
        // given
        googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .maxRetries(3)
                .build();

        WebSearchContentRetriever contentRetriever = WebSearchContentRetriever.from(googleSearchEngine);
        Query query = Query.from("What is the latest currency exchange rates for the US Dollar and Euro");

        // when
        List<Content> contents = contentRetriever.retrieve(query);
        System.out.println("contents: " + contents);

        // then
        assertThat(contents)
                .as("At least one content should be contains 'us dollar' and 'euro' ignoring case")
                .anySatisfy(content -> {
                            assertThat(content.textSegment().text())
                                    .containsIgnoringCase("us dollar")
                                    .containsIgnoringCase("euro");
                            assertThat(content.textSegment().metadata().get("url"))
                                    .startsWith("https://");
                        }
                );
    }

    @Test
    void should_retrieve_web_content_with_google_and_use_AiServices_to_summary_response () {
        // given
        googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .maxRetries(3)
                .build();

        WebSearchContentRetriever contentRetriever = WebSearchContentRetriever.from(googleSearchEngine);

        String query = "My family is coming to visit me in Madrid next week, list the best tourist activities suitable for the whole family";

        // when
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(contentRetriever)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String answer = assistant.answer(query);
        System.out.println(answer);

        // then
        assertThat(answer)
                .as("At least the string result should be contains 'madrid' and 'tourist' ignoring case")
                .containsIgnoringCase("Madrid")
                .containsIgnoringCase("Royal Palace of Madrid");
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return googleSearchEngine;
    }
}
