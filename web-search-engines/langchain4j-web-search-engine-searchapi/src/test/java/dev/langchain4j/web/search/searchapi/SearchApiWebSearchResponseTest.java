package dev.langchain4j.web.search.searchapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SearchApi sends its fields in snake_case. The naming lives on the codec rather than on the
 * response types, so it is worth pinning here.
 */
class SearchApiWebSearchResponseTest {

    @Test
    void should_read_a_response_whose_fields_are_snake_case() {
        SearchApiWebSearchResponse response = SearchApiJsonUtils.fromJson(
                """
                {
                  "search_metadata": {"id": "search_1", "status": "Success"},
                  "search_parameters": {"engine": "google", "q": "langchain4j"},
                  "search_information": {"total_results": 2},
                  "pagination": {"current": 1},
                  "organic_results": [
                    {"position": "1", "title": "LangChain4j", "link": "https://langchain4j.dev",
                     "snippet": "Supercharge your Java application with the power of LLMs"},
                    {"position": "2", "title": "GitHub", "link": "https://github.com/langchain4j/langchain4j"}
                  ]
                }""",
                SearchApiWebSearchResponse.class);

        assertThat(response.getSearchMetadata()).containsEntry("status", "Success");
        assertThat(response.getSearchParameters()).containsEntry("q", "langchain4j");
        assertThat(response.getSearchInformation()).containsEntry("total_results", 2);
        assertThat(response.getPagination()).containsEntry("current", 1);

        assertThat(response.getOrganicResults()).hasSize(2);
        assertThat(response.getOrganicResults().get(0).getTitle()).isEqualTo("LangChain4j");
        assertThat(response.getOrganicResults().get(0).getLink()).isEqualTo("https://langchain4j.dev");
        assertThat(response.getOrganicResults().get(0).getPosition()).isEqualTo("1");
        assertThat(response.getOrganicResults().get(0).getSnippet())
                .isEqualTo("Supercharge your Java application with the power of LLMs");
        assertThat(response.getOrganicResults().get(1).getSnippet()).isNull();
    }

    @Test
    void should_ignore_fields_it_does_not_know() {
        SearchApiWebSearchResponse response = SearchApiJsonUtils.fromJson(
                "{\"organic_results\":[],\"a_brand_new_field\":\"whatever\"}", SearchApiWebSearchResponse.class);

        assertThat(response.getOrganicResults()).isEmpty();
    }
}
