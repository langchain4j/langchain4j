package dev.langchain4j.web.search.brave;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class BraveWebSearchEngineIT extends WebSearchEngineIT {

    @Override
    protected WebSearchEngine searchEngine() {
        // Parameters for the BraveWebSearchEngine
        String baseUrl = "https://api.search.brave.com";
        String apiKey = ""; // Replace with your API key
        Duration timeout = Duration.ofSeconds(10);
        Integer count = WebSearchEngineIT.EXPECTED_MAX_RESULTS;
        String safeSearch = "off";
        String resultFilter = "web";
        String freshness = "";
        Map<String, Object> params = new HashMap<>();
        params.put("count",count);
        params.put("safeSearch",safeSearch);
        params.put("resultFilter",resultFilter);
        params.put("freshness",freshness);
        return new BraveWebSearchEngine(baseUrl,apiKey,timeout,params);
    }
}

