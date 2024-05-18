package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.web.search.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static dev.langchain4j.web.search.google.customsearch.GoogleCustomWebSearchEngine.ImageSearchResult;

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_SEARCH_ENGINE_ID", matches = ".*")
class GoogleCustomWebSearchEngineIT extends WebSearchEngineIT {

    WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.withApiKeyAndCsi(
                                            System.getenv("GOOGLE_API_KEY"),
                                            System.getenv("GOOGLE_SEARCH_ENGINE_ID"));

    @Test
    void should_return_google_web_results_with_search_information() {
        // given
        String query = "What is LangChain4j project?";

        // when
        WebSearchResults results = googleSearchEngine.search(query);

        // then
        assertThat(results.searchMetadata()).isNotNull();
        assertThat(results.searchInformation().totalResults()).isGreaterThan(0);
        assertThat(results.results().size()).isGreaterThan(0);
    }

    @Test
    void should_return_google_safe_web_results_in_spanish_language() {
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
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("argentina"));
    }

    @Test
    void should_return_google_results_of_the_second_page_and_log_http_req_resp() {
        // given
        WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .build();

        String query = "What is the weather in Porto?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .maxResults(5)
                .startPage(2)
                .build();

        // when
        WebSearchResults webSearchResults = googleSearchEngine.search(webSearchRequest);

        // then
        assertThat(webSearchResults.results())
                .as("At least the string result should be contains 'weather' and 'Porto' ignoring case")
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("weather")
                        .containsIgnoringCase("porto"));
    }

    @Test
    void should_return_google_results_using_and_fix_startpage_by_startindex(){
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
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("Java")
                        .containsIgnoringCase("AI"));
    }

    @Test
    void should_return_google_result_using_additional_params() {
        // given
        WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .build();

        String query = "What is LangChain4j project?";
        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("dateRestrict", "w[2]");
        additionalParams.put("linkSite", "https://github.com/langchain4j/langchain4j");
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .additionalParams(additionalParams)
                .build();

        // when
        List<WebSearchOrganicResult> results = googleSearchEngine.search(webSearchRequest).results();

        // then
        assertThat(results)
                .as("At least one result should be contains 'Java' and 'AI' ignoring case")
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("Java")
                        .containsIgnoringCase("github.com/langchain4j/langchain4j"));
    }

    @Test
    void should_return_google_result_with_images_related() {
        // given
        WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .includeImages(true) // execute an additional search, searchType: image
                .logRequestResponse(true)
                .build();

        String query = "Which top 2024 universities to study computer science?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .build();

        // when
        WebSearchResults webSearchResults = googleSearchEngine.search(webSearchRequest);

        // then
        assertThat(webSearchResults.searchMetadata().get("searchType").toString()).isEqualTo("web"); // searchType: web
        assertThat(webSearchResults.searchInformation().metadata().get("images")).isOfAnyClassIn(ArrayList.class, List.class); // should add images related to the query
        assertThat((List<ImageSearchResult>)webSearchResults.searchInformation().metadata().get("images")) // Get images from searchInformation.metadata
                .as("At least one image result should be contains title, link, contextLink and thumbnailLink")
                .anySatisfy(image -> {
                    assertThat(image.title()).isNotNull();
                    assertThat(image.imageLink().toString()).startsWith("http");
                    assertThat(image.contextLink().toString()).startsWith("http");
                    assertThat(image.thumbnailLink().toString()).startsWith("http");
                });
        assertThat(webSearchResults.results()) // Get web results
                .as("At least the string result should be contains 'university' and 'ranking' ignoring case")
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("university")
                        .containsIgnoringCase("ranking"));

    }

    @Test
    void should_return_google_image_result_with_param_searchType_image() {
        // given
        String query = "How will be the weather next week in Lisbon and Porto cities?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .additionalParams(singletonMap("searchType", "image"))
                .build();

        // when
        WebSearchResults webSearchResults = googleSearchEngine.search(webSearchRequest);

        // then
        assertThat(webSearchResults.searchMetadata().get("searchType").toString()).isEqualTo("images"); // searchType: images
        assertThat(webSearchResults.results()) // Get images as search results
                .as("At least the snippet should be contains 'weather' and 'Porto' ignoring case")
                .anySatisfy(result -> assertThat(result.title())
                        .containsIgnoringCase("weather")
                        .containsIgnoringCase("porto"))
                .anySatisfy(result -> assertThat(result.url().toString())
                        .startsWith("http"))
                .anySatisfy(result -> assertThat(result.metadata().get("mimeType"))
                        .startsWith("image"))
                .anySatisfy(result -> assertThat(result.metadata().get("imageLink"))
                        .isEqualTo(result.url().toString()))
                .anySatisfy(result -> assertThat(result.metadata().get("contextLink"))
                        .startsWith("http"))
                .anySatisfy(result -> assertThat(result.metadata().get("thumbnailLink"))
                        .startsWith("http"));
    }
    @Override
    protected WebSearchEngine searchEngine() {
        return googleSearchEngine;
    }
}
