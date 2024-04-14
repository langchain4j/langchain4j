package dev.langchain4j.web.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class WebSearchToolIT extends WebSearchIT{

    @Test
    void should_return_pretty_result_as_a_tool(){
        // given
        WebSearchTool webSearchTool = WebSearchTool.from(searchEngine());
        String searchTerm = "What is LangChain4j project?";

        // when
        String strResult = webSearchTool.runSearch(searchTerm);

        // then
        assertThat(strResult).isNotBlank();
        assertThat(strResult)
                .as("At least the string result should be contains 'java' and 'AI' ignoring case")
                .containsIgnoringCase("Java")
                .containsIgnoringCase("AI");
    }
}
