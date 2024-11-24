package dev.langchain4j.web.search.brave;

import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.brave.BraveWebSearchEngine;

import java.time.Duration;

public class BraveSearchDemo {
    public static void main(String[] args) {
        // Parameters for the BraveWebSearchEngine
        String baseUrl = "https://api.search.brave.com/res/v1/web/";
        String apiKey = ""; // Replace with your API key
        Duration timeout = Duration.ofSeconds(10);
        Integer count = 10;
        String safeSearch = "moderate";
        String resultFilter = "web";
        String freshness = "day";

        // Create an instance of BraveWebSearchEngine
        BraveWebSearchEngine braveSearchEngine = new BraveWebSearchEngine(
                baseUrl,
                apiKey,
                timeout,
                count,
                safeSearch,
                resultFilter,
                freshness
        );

        // Example query
        String query = "brave search engine";

        // Perform the search
        try {
            System.out.println("Searching for: " + query);
            WebSearchResults results = braveSearchEngine.search(query);
            System.out.println("Search results: " + results);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to fetch search results.");
        }
    }
}
