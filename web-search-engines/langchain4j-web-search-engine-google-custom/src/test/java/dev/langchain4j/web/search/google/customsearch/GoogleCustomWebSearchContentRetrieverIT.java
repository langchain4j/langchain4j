package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetrieverIT;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.WebSearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_SEARCH_ENGINE_ID", matches = ".*")
class GoogleCustomWebSearchContentRetrieverIT extends WebSearchContentRetrieverIT {

    WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
            .apiKey(System.getenv("GOOGLE_API_KEY"))
            .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
            .logRequests(true)
            .logResponses(true)
            .build();

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    interface Assistant {

        String answer(String userMessage);
    }

    @Test
    void should_retrieve_web_content_with_google_and_use_AiServices_to_summary_response() {

        // given
        WebSearchContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(googleSearchEngine)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        String query = "What features does LangChain4j have?";

        // when
        String answer = assistant.answer(query);

        // then
        assertThat(answer).contains("RAG");
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return googleSearchEngine;
    }
}
