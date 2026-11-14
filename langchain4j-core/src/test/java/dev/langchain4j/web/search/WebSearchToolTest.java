package dev.langchain4j.web.search;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.net.URI;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebSearchToolTest {

    WebSearchEngine webSearchEngine;

    @BeforeEach
    void mockWebSearchEngine() {
        webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString()))
                .thenReturn(new WebSearchResults(
                        WebSearchInformationResult.from(3L, 1, new HashMap<>()),
                        asList(
                                WebSearchOrganicResult.from(
                                        "title 1", URI.create("https://google.com"), "snippet 1", "content 1"),
                                WebSearchOrganicResult.from(
                                        "title 2",
                                        URI.create("https://docs.langchain4j.dev"),
                                        "snippet 2",
                                        "content 2"),
                                WebSearchOrganicResult.from(
                                        "title 3",
                                        URI.create("https://github.com/dewitt/opensearch/blob/master/README.md"),
                                        "snippet 3",
                                        "content 3"))));
    }

    @AfterEach
    void resetWebSearchEngine() {
        reset(webSearchEngine);
    }

    @Test
    void should_build_webSearchTool() {
        // given
        String searchTerm = "Any text to search";
        WebSearchTool webSearchTool = WebSearchTool.from(webSearchEngine);

        // when
        String strResult = webSearchTool.searchWeb(searchTerm);

        // then
        assertThat(strResult).isNotBlank();
        assertThat(strResult)
                .as("At least one result should be contains 'title 1' and 'https://google.com' and 'content 1'")
                .contains("Title: title 1\nSource: https://google.com\nContent:\ncontent 1");

        verify(webSearchEngine).search(searchTerm);
        verifyNoMoreInteractions(webSearchEngine);
    }

    @Test
    void should_expose_query_parameter_by_name() {
        // when
        ToolSpecification toolSpecification =
                ToolSpecifications.toolSpecificationsFrom(WebSearchTool.class).get(0);

        // then
        assertThat(toolSpecification.parameters().properties()).containsOnlyKeys("query");
        assertThat(toolSpecification.parameters().required()).containsExactly("query");
    }
}
