package dev.langchain4j.web.search.tavily;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class TavilyWebSearchEngineUnresolvableUrlTest {

    private static final String RESPONSE = """
        {
          "query": "langchain4j",
          "results": [
            {"title": "Broken", "url": "://missing-scheme", "content": "broken", "score": 0.9},
            {"title": "Good", "url": "https://example.com/good", "content": "good", "score": 0.8}
          ]
        }\
        """;

    @Test
    void search_skips_results_with_unresolvable_url() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/search", exchange -> {
            byte[] body = RESPONSE.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                    .baseUrl("http://localhost:" + server.getAddress().getPort())
                    .apiKey("test-key")
                    .build();

            WebSearchResults results = engine.search(WebSearchRequest.from("langchain4j"));

            assertThat(results.results())
                    .extracting(WebSearchOrganicResult::url)
                    .containsExactly(URI.create("https://example.com/good"));
        } finally {
            server.stop(0);
        }
    }
}
