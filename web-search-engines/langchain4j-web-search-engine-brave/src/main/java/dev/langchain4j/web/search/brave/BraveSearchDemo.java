package dev.langchain4j.web.search.brave;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.brave.BraveWebSearchEngine;

import java.time.Duration;

public class BraveSearchDemo {
    public static void main(String[] args) {
        // Parameters for the BraveWebSearchEngine
        String baseUrl = "https://api.search.brave.com/res/v1/web/";
        String apiKey = "BSAJsGw6fk3ePFkR_dBzlsY7UYovyDa"; // Replace with your API key
        Duration timeout = Duration.ofSeconds(10);
        Integer count = 30;
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
        String query = "I want to plan my visit to Hyderabad India help me plan my trip ";

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
