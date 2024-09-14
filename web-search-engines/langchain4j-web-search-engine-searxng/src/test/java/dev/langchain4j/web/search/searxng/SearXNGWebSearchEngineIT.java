package dev.langchain4j.web.search.searxng;

import java.time.Duration;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;

@EnabledIfEnvironmentVariable(named = "SEARXNG_BASE_URL", matches = ".+")
class SearXNGWebSearchEngineIT extends WebSearchEngineIT {

    WebSearchEngine webSearchEngine = SearXNGWebSearchEngine.builder().baseUrl(System.getenv("SEARXNG_BASE_URL")).timeout(Duration.ofSeconds(10l)).build();

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
