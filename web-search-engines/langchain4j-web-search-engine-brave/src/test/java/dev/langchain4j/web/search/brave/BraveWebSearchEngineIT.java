package dev.langchain4j.web.search.brave;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class BraveWebSearchEngineIT extends WebSearchEngineIT {

    WebSearchEngine webSearchEngine = BraveWebSearchEngine.withApiKey(System.getenv("BRAVE_API_KEY"));
    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }



}

