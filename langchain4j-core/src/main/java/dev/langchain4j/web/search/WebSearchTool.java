package dev.langchain4j.web.search;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class WebSearchTool {

    private final WebSearchEngine searchEngine;

    public WebSearchTool(WebSearchEngine searchEngine) {
        this.searchEngine = ensureNotNull(searchEngine, "searchEngine");
    }

    /**
     * Runs a search query on the web search engine and returns a pretty-string representation of the search results.
     *
     * @param searchTerm the search user query
     * @return           a pretty-string representation of the search results
     */
    @Tool("This tool can be used to perform web searches using search engines such as Google, particularly when seeking information about recent events.")
    public String searchWeb(@P("Web search query") String searchTerm) {
        WebSearchResults results = searchEngine.search(searchTerm);
        return format(results);
    }

    private String format(WebSearchResults results){
        return results.results()
                .stream()
                .map(organicResult -> "Title: " + organicResult.title() + "\n"
                        + "URL Source: " + organicResult.url().toString() + "\n"
                        + (organicResult.content() != null ? "Content:" + "\n" + organicResult.content() : "Snippet:" + "\n" + organicResult.snippet()))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Creates a new WebSearchTool with the specified web search engine.
     *
     * @param searchEngine the web search engine to use for searching the web
     * @return a new WebSearchTool
     */
    public static WebSearchTool from(WebSearchEngine searchEngine) {
        return new WebSearchTool(searchEngine);
    }
}
