package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetrieverIT;
import dev.langchain4j.rag.query.Query;
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

    @Test
    void should_retrieve_content_with_google_additional_params() {
        // given
        googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .maxRetries(3)
                .build();

        WebSearchContentRetriever contentRetriever = WebSearchContentRetriever.from(googleSearchEngine);
        Query query = Query.from("What is the current weather in New York?");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents)
                .as("At least one content should be contains 'weather' and 'New York' ignoring case")
                .anySatisfy(content -> {
                            assertThat(content.textSegment().text())
                                    .containsIgnoringCase("weather")
                                    .containsIgnoringCase("New York");
                            assertThat(content.textSegment().metadata().get("url"))
                                    .startsWith("https://");
                            assertThat(content.textSegment().metadata().get("title"))
                                    .isNotBlank();
                        }
                );
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return googleSearchEngine;
    }
}
