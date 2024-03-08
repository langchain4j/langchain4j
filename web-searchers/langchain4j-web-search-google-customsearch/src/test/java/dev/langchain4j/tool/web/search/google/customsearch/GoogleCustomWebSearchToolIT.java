package dev.langchain4j.tool.web.search.google.customsearch;

import dev.langchain4j.data.web.WebResult;
import dev.langchain4j.tool.web.search.WebSearchTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCustomWebSearchToolIT {

    WebSearchTool googleSearchTool = GoogleCustomWebSearchTool.withApiKeyAndCsi(
                                            System.getenv("GOOGLE_API_KEY"),
                                            System.getenv("GOOGLE_SEARCH_ENGINE_ID"));


    @Test
    void should_return_run_result_as_pretty_string() {
        // given
        String query = "What is LangChain4j project?";

        // when
        String strResult = googleSearchTool.runSearch(query);

        // then
        assertThat(strResult)
                .as("At least the string result should be contains 'java' and 'AI' ignoring case")
                .containsIgnoringCase("Java")
                .containsIgnoringCase("AI");
    }

    @Test
    void should_return_default_search_results() {
        // given
        String query = "Who won the FIFA World Cup 2022?";

        // when
        List<WebResult> results = googleSearchTool.searchResults(query);

        // then
        assertThat(results)
                .as("At least one result should be contains 'argentina' ignoring case")
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("argentina"));
    }

    @Test
    void should_return_ten_web_results_of_the_second_page() {
        // given
        WebSearchTool googleSearchTool = GoogleCustomWebSearchTool.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .numberOfPage(2)
                .maxResults(10)
                .build();

        String query = "What is the weather in New York?";

        // when
        String strResult = googleSearchTool.runSearch(query);

        // then
        assertThat(strResult)
                .as("At least the string result should be contains 'weather' and 'New York' ignoring case")
                .containsIgnoringCase("weather")
                .containsIgnoringCase("New York");
    }

    @Test
    void should_return_web_results_of_google_france(){
        // given
        WebSearchTool googleSearchTool = GoogleCustomWebSearchTool.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .geoLocation("fr")
                .build();

        String query = "Qui est l'actuel président de la France ?";

        // when
        String strResults = googleSearchTool.runSearch(query);
        System.out.println(strResults);

        // then
        assertThat(strResults)
                .as("At least one result should be contains 'Emmanuel Macro' ignoring case")
                .containsIgnoringCase("Emmanuel Macro");
    }
}
