package dev.langchain4j.agent.tool.websearch;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.google.customsearch.GoogleCustomWebSearchEngine;
import lombok.Builder;

/**
 * Represents a {@link Tool} for performing web searches using the Google Custom Search Engine.
 * <p>
 * Can be used by a language model that calls functions to search the web using a web search engine to return organic web results.
 */
public class GoogleCustomWebSearchTool{

    private final WebSearchEngine googleCustomWebSearchEngine;

    /**
     * Constructs a new instance of the GoogleCustomWebSearchTool.
     *
     * @param apiKey        the API key for accessing the Google Custom Search Engine
     * @param csi           the CSI (Custom Search ID) parameter for the search engine
     * @param siteRestrict  a boolean indicating whether to restrict the search to a specific site
     */
    @Builder
    public GoogleCustomWebSearchTool(String apiKey,
                                     String csi,
                                     Boolean siteRestrict) {
        this.googleCustomWebSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(apiKey)
                .csi(csi)
                .siteRestrict(siteRestrict)
                .build();
    }

    /**
     * Creates a new instance of the GoogleCustomWebSearchTool with the specified API key and CSI.
     *
     * @param apiKey  the API key for accessing the Google Custom Search Engine
     * @param csi     the CSI (Custom Search ID) parameter for the search engine.
     * @return        a new instance of the GoogleCustomWebSearchTool
     */
    public static GoogleCustomWebSearchTool withApiKeyAndCsi(String apiKey, String csi){
        return GoogleCustomWebSearchTool.builder().apiKey(apiKey).csi(csi).build();
    }

    /**
     * Runs a web search using the Google Custom Search Engine.
     *
     * @param searchTerm  the search terms to look for
     * @return            the search results as a pretty-string
     */
    @Tool({"MUST be used to search the web using a web search engine for organic web results.",
            "An organic web result is a title, a link, a content and metadata of a web page"})
    public String runSearch(@P("The search terms is equal to the user message content that looking for") String searchTerm) {
        return googleCustomWebSearchEngine.runSearch(searchTerm);
    }
}
