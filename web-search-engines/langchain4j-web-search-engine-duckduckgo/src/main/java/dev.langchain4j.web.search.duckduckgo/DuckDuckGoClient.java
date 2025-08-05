package dev.langchain4j.web.search.duckduckgo;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class DuckDuckGoClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final AtomicLong lastRequestTime = new AtomicLong(0);
    private static final long MIN_REQUEST_INTERVAL_MS = 1500;

    private static final String HTML_SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final String API_SEARCH_URL = "https://api.duckduckgo.com/";

    private final HttpClient httpClient;
    private final Duration timeout;

    DuckDuckGoClient(HttpClient httpClient, Duration timeout) {
        this.httpClient = httpClient;
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
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

    private List<DuckDuckGoSearchResult> performHtmlSearch(String query, int maxResults) throws IOException {
        enforceRateLimit();

        String formData = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) +
                "&b=" +
                "&kl=us-en";

        HttpRequest request = HttpRequest.builder()
                .url(HTML_SEARCH_URL)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .method(HttpMethod.POST)
                .body(formData)
                .build();

        SuccessfulHttpResponse response = httpClient.execute(request);

        if (response.statusCode() == 202) {
            throw new IOException("DuckDuckGo HTML search blocked or invalid response");
        }

        Document doc = Jsoup.parse(response.body());
        List<DuckDuckGoSearchResult> results = parseHtmlResults(doc, maxResults);

        if (results.isEmpty()) {
            throw new IOException("No results found in HTML response");
        }

        return results;
    }

    private List<DuckDuckGoSearchResult> performApiSearch(String query, int maxResults) throws IOException {
        String url = API_SEARCH_URL + "?q=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8) +
                "&format=json&no_html=1&skip_disambig=1";

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

        String[] resultSelectors = {
                "div.web-result",
                "div.result",
                "div[class*='result']",
                ".links_main"
        };

        Elements resultElements = new Elements();
        for (String selector : resultSelectors) {
            resultElements = doc.select(selector);
            if (!resultElements.isEmpty()) break;
        }

        for (Element element : resultElements) {
            if (results.size() >= maxResults) break;

            Element titleElement = element.selectFirst("h2 a, .result__title a, a.result__a, h3 a");
            if (titleElement == null) continue;

            String title = titleElement.text().trim();
            String url = titleElement.attr("href");

            String snippet = "";
            Element snippetElement = element.selectFirst(".result__snippet, .result-snippet, .snippet");
            if (snippetElement != null) {
                snippet = snippetElement.text().trim();
            }

            if (!title.isEmpty() && isValidUrl(url)) {
                url = cleanUrl(url);
                results.add(DuckDuckGoSearchResult.builder()
                        .title(title)
                        .url(url)
                        .snippet(snippet)
                        .build());
            }
        }

        return results;
    }

    private List<DuckDuckGoSearchResult> parseApiResponse(String json, int maxResults) {
        List<DuckDuckGoSearchResult> results = new ArrayList<>();

        try {
            String abstractText = extractJsonValue(json, "Abstract");
            String abstractUrl = extractJsonValue(json, "AbstractURL");
            String abstractSource = extractJsonValue(json, "AbstractSource");

            if (!abstractText.isEmpty() && !abstractUrl.isEmpty()) {
                results.add(DuckDuckGoSearchResult.builder()
                        .title(abstractSource.isEmpty() ? "Abstract" : abstractSource)
                        .url(abstractUrl)
                        .snippet(abstractText)
                        .build());
            }

            String answer = extractJsonValue(json, "Answer");
            if (!answer.isEmpty()) {
                results.add(DuckDuckGoSearchResult.builder()
                        .title("Direct Answer")
                        .url("https://duckduckgo.com/?q=" + URLEncoder.encode(answer, StandardCharsets.UTF_8))
                        .snippet(answer)
                        .build());
            }

        } catch (Exception ignored) {}

        return results.stream().limit(maxResults).collect(Collectors.toList());
    }

    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return "";

            start += pattern.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return "";

            return json.substring(start, end)
                    .replace("\\\"", "\"")
                    .replace("\\n", " ")
                    .replace("\\r", "")
                    .trim();
        } catch (Exception e) {
            return "";
        }
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

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        long elapsed = now - last;

        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Rate limiting interrupted", e);
            }
        }

        lastRequestTime.set(System.currentTimeMillis());
    }
}
