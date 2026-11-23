package dev.langchain4j.web.search.searchapi;

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

class SearchApiWebSearchEngineUnresolvableUrlTest {

    private static final String RESPONSE = """
        {
          "search_information": {"total_results": 2},
          "organic_results": [
            {"position": "1", "title": "Broken", "link": "://missing-scheme"},
            {"position": "2", "title": "Good", "link": "https://example.com/good"}
          ]
        }\
        """;

    @Test
    void search_skips_results_with_unresolvable_url() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/search", exchange -> {
            byte[] body = RESPONSE.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            SearchApiWebSearchEngine engine = SearchApiWebSearchEngine.builder()
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
