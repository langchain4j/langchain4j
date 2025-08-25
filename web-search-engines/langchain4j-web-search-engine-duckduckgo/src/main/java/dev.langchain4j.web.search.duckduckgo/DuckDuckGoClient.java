package dev.langchain4j.web.search.duckduckgo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class DuckDuckGoClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String HTML_SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final String API_SEARCH_URL = "https://api.duckduckgo.com/";

    private final HttpClient httpClient;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    DuckDuckGoClient(HttpClient httpClient, Duration timeout) {
        this.httpClient = httpClient;
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        this.objectMapper = new ObjectMapper();
    }

    public List<DuckDuckGoSearchResult> search(String query, int maxResults) {
        try {
            return performHtmlSearch(query, maxResults);
        } catch (Exception e) {
            try {
                return performApiSearch(query, maxResults);
            } catch (Exception apiE) {
                return Collections.emptyList();
            }
        }
    }

    public CompletableFuture<List<DuckDuckGoSearchResult>> searchAsync(String query, int maxResults) {
        return CompletableFuture.supplyAsync(() -> search(query, maxResults));
    }

    private List<DuckDuckGoSearchResult> performHtmlSearch(String query, int maxResults) throws IOException {
        String formData = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&b=&kl=us-en";

        HttpRequest request = HttpRequest.builder()
                .url(HTML_SEARCH_URL)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .method(HttpMethod.POST)
                .body(formData)
                .build();

        SuccessfulHttpResponse response = httpClient.execute(request);

        if (response.statusCode() == 202) {
            throw new IOException("DuckDuckGo HTML search blocked");
        }

        Document doc = Jsoup.parse(response.body());
        List<DuckDuckGoSearchResult> results = parseHtmlResults(doc, maxResults);

        if (results.isEmpty()) {
            throw new IOException("No results found");
        }

        return results;
    }

    private List<DuckDuckGoSearchResult> performApiSearch(String query, int maxResults) throws IOException {
        String url = API_SEARCH_URL + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=json&no_html=1&skip_disambig=1";

        HttpRequest request = HttpRequest.builder()
                .url(url)
                .addHeader("User-Agent", "LangChain4j-DuckDuckGo/1.0")
                .method(HttpMethod.GET)
                .build();

        SuccessfulHttpResponse response = httpClient.execute(request);
        return parseApiResponse(response.body(), maxResults);
    }

    private List<DuckDuckGoSearchResult> parseHtmlResults(Document doc, int maxResults) {
        List<DuckDuckGoSearchResult> results = new ArrayList<>();

        String[] selectors = {"div.web-result", "div.result", ".links_main"};
        Elements resultElements = new Elements();

        for (String selector : selectors) {
            resultElements = doc.select(selector);
            if (!resultElements.isEmpty()) break;
        }

        for (Element element : resultElements) {
            if (results.size() >= maxResults) break;

            Element titleElement = element.selectFirst("h2 a, .result__title a, h3 a");
            if (titleElement == null) continue;

            String title = titleElement.text().trim();
            String url = titleElement.attr("href");

            if (title.isEmpty() || !isValidUrl(url)) continue;

            String snippet = "";
            Element snippetElement = element.selectFirst(".result__snippet, .snippet");
            if (snippetElement != null) {
                snippet = snippetElement.text().trim();
            }

            results.add(DuckDuckGoSearchResult.builder()
                    .title(title)
                    .url(cleanUrl(url))
                    .snippet(snippet)
                    .build());
        }

        return results;
    }

    private List<DuckDuckGoSearchResult> parseApiResponse(String json, int maxResults) {
        List<DuckDuckGoSearchResult> results = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(json);

            String abstractText = getJsonText(rootNode, "Abstract");
            String abstractUrl = getJsonText(rootNode, "AbstractURL");
            if (!abstractText.isEmpty() && !abstractUrl.isEmpty()) {
                results.add(DuckDuckGoSearchResult.builder()
                        .title("Abstract")
                        .url(abstractUrl)
                        .snippet(abstractText)
                        .build());
            }

            String answer = getJsonText(rootNode, "Answer");
            if (!answer.isEmpty()) {
                results.add(DuckDuckGoSearchResult.builder()
                        .title("Answer")
                        .url("https://duckduckgo.com")
                        .snippet(answer)
                        .build());
            }

        } catch (Exception ignored) {
        }

        return results.stream().limit(maxResults).collect(Collectors.toList());
    }

    private String getJsonText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText("").trim() : "";
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (url.contains("duckduckgo.com") && !url.contains("/l/")) return false;
        return url.startsWith("https://") || url.startsWith("http://") || url.startsWith("//");
    }

    private String cleanUrl(String url) {
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (url.startsWith("http://")) {
            url = url.replace("http://", "https://");
        }
        return url;
    }
}
