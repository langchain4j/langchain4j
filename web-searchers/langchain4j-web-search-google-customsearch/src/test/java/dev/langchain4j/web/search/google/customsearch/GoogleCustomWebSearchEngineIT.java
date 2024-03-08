package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.WebSearchEngine;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.web.search.WebSearchRequest.webSearchRequest;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleCustomWebSearchEngineIT {

    WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.withApiKeyAndCsi(
                                            System.getenv("GOOGLE_API_KEY"),
                                            System.getenv("GOOGLE_SEARCH_ENGINE_ID"));

    @Test
    void should_return_default_web_results_with_search_information_and_pagination() {
        // given
        String query = "What is the weather in New York?";

        // when
        WebSearchResults results = googleSearchEngine.search(query);

        // then
        assertThat(results.searchMetadata()).isNotNull();
        assertThat(results.searchInformation().totalResults()).isGreaterThan(0);
        assertThat(results.pagination().current()).isEqualTo(1);
        assertThat(results.results().size()).isGreaterThan(0);
    }

    @Test
    void should_return_safe_web_results_in_spanish_language() {
        // given
        String query = "Who won the FIFA World Cup 2022?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .language("lang_es")
                .safeSearch(true)
                .build();

        // when
        List<WebSearchOrganicResult> results = googleSearchEngine.search(webSearchRequest).results();

        // then
        assertThat(results)
                .as("At least one result should be contains 'argentina' ignoring case")
                .anySatisfy(result -> assertThat(result.content())
                        .containsIgnoringCase("argentina"));
    }

    @Test
    void should_return_web_results_of_the_second_page_and_log_http_req_resp() {
        // given
        WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .build();

        String query = "What is the weather in New York?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .startPage(2)
                .build();

        // when
        WebSearchResults webSearchResults = googleSearchEngine.search(webSearchRequest);

        // then
        assertThat(webSearchResults.results())
                .as("At least the string result should be contains 'weather' and 'New York' ignoring case")
                .anySatisfy(result -> assertThat(result.content())
                        .containsIgnoringCase("weather")
                        .containsIgnoringCase("New York"));
        assertThat(webSearchResults.pagination().current()).isEqualTo(2);
    }

    //
    @Test
    void should_return_web_results_with_geo_location(){
        // given
        WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .build();

        String query = "Qui est le actuel président de la France ?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .geoLocation("fr")
                .build();

        // when
        List<WebSearchOrganicResult> webSearchOrganicResults = googleSearchEngine.search(webSearchRequest).results();

        // then
        assertThat(webSearchOrganicResults)
                .as("At least one result should be contains 'Emmanuel Macro' ignoring case")
                .anySatisfy(result -> assertThat(result.content())
                        .containsIgnoringCase("Emmanuel Macro"));
    }

    @Test
    void should_return_web_results_using_and_fix_startpage_by_startindex(){
        // given
        String query = "What is LangChain4j project?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .language("lang_en")
                .startPage(1) //user bad request
                .startIndex(15)
                .build();

        // when
        WebSearchResults webSearchResults = googleSearchEngine.search(webSearchRequest);

        // then
        assertThat(webSearchResults.results())
                .as("At least one result should be contains 'Java' and 'AI' ignoring case")
                .anySatisfy(result -> assertThat(result.content())
                        .containsIgnoringCase("Java")
                        .containsIgnoringCase("AI"));
        assertThat(webSearchResults.pagination().current()).isEqualTo(2);
    }

    @Test
    void should_return_max_web_results_given(){
        // given
        String query = "What is LangChain4j project?";
        WebSearchRequest webSearchRequest = webSearchRequest(query, 5);

        // when
        List<WebSearchOrganicResult> results = googleSearchEngine.search(webSearchRequest).results();

        // then
        assertThat(results).hasSize(5);

    }

    @Test
    void should_return_web_result_using_additional_params(){
        // given
        WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .build();

        String query = "What is LangChain4j project?";
        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("dateRestrict","w[2]");
        additionalParams.put("linkSite","https://github.com/langchain4j/langchain4j");
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .additionalParams(additionalParams)
                .build();

        // when
        List<WebSearchOrganicResult> results = googleSearchEngine.search(webSearchRequest).results();

        // then
        assertThat(results)
                .as("At least one result should be contains 'Java' and 'AI' ignoring case")
                .anySatisfy(result -> assertThat(result.content())
                        .containsIgnoringCase("Java")
                        .containsIgnoringCase("github.com/langchain4j/langchain4j"));
    }
}
