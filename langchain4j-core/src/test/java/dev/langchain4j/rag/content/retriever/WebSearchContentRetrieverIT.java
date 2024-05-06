package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchEngineIT;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class WebSearchContentRetrieverIT extends WebSearchEngineIT {

    @Test
    void should_retrieve_web_page_as_content() {
        // given
        WebSearchContentRetriever contentRetriever = WebSearchContentRetriever.from(searchEngine());
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
}
