import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetrieverIT;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiContentRetrieverIT extends WebSearchContentRetrieverIT {

    private final WebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
            .apiKey(System.getenv("SEARCHAPI_API_KEY"))
            .build();

    private final ChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O_MINI)
            .logRequests(true)
            .build();

    @Override
    protected WebSearchEngine searchEngine() {
        return searchEngine;
    }

    @Test
    void should_retrieve_web_content_with_search_Api_and_use_AiServices_to_summary_response() {

        // given
        WebSearchContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(searchEngine)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        String query = "What features does LangChain4j have?";

        // when
        String answer = assistant.answer(query);

        // then
        assertThat(answer).containsIgnoringCase("integration");
    }

    interface Assistant {

        String answer(String userMessage);
    }
}
