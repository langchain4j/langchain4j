package dev.langchain4j.tool.web.search;

import dev.langchain4j.data.web.WebResult;
import dev.langchain4j.internal.ValidationUtils;

import java.util.List;
import java.util.stream.Collectors;

public interface WebSearchTool {

    default String runSearch(String query) {
        List<WebResult> results = searchResults(query);
        ValidationUtils.ensureNotEmpty(results, "No web search results found for query: " + query);
        return results.stream()
                .map(WebResult::snippet)
                .collect(Collectors.joining("\n "));
    }


    List<WebResult> searchResults(String query);
}
