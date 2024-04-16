package dev.langchain4j.web.search.google.customsearch;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchTool;
import dev.langchain4j.web.search.WebSearchToolIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "GOOGLE_SEARCH_ENGINE_ID", matches = ".*")
class GoogleCustomWebSearchToolIT extends WebSearchToolIT {

    WebSearchEngine googleSearchEngine = GoogleCustomWebSearchEngine.withApiKeyAndCsi(
            System.getenv("GOOGLE_API_KEY"),
            System.getenv("GOOGLE_SEARCH_ENGINE_ID"));

    @Test
    void should_execute_tool_with_google_additional_params(){
        // given
        googleSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .csi(System.getenv("GOOGLE_SEARCH_ENGINE_ID"))
                .logRequestResponse(true)
                .maxRetries(3)
                .build();
        WebSearchTool webSearchTool = WebSearchTool.from(googleSearchEngine);
        String query = "What is LangChain4j project?";

        // when
        String strResult = webSearchTool.runSearch(query);

        // then
        assertThat(strResult).isNotBlank();
        assertThat(strResult)
                .as("At least the string result should be contains 'java' and 'AI' ignoring case")
                .containsIgnoringCase("Java")
                .containsIgnoringCase("AI");
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return googleSearchEngine;
    }
}
