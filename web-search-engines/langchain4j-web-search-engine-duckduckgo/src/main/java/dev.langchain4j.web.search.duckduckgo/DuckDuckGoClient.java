package dev.langchain4j.web.search.duckduckgo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

class DuckDuckGoClient {

    private static final String[] SEARCH_URLS = {
            "https://duckduckgo.com/html/?q=",
            "https://html.duckduckgo.com/html/?q=",
            "https://lite.duckduckgo.com/lite/?q="
    };

    private final int timeoutMs;

    public DuckDuckGoClient(Duration timeout) {
        this.timeoutMs = (int) timeout.toMillis();
    }

    public static DuckDuckGoClientBuilder builder() {
        return new DuckDuckGoClientBuilder();
    }

    public List<DuckDuckGoSearchResult> search(String query, int maxResults) {
        for (String baseUrl : SEARCH_URLS) {
            try {
                List<DuckDuckGoSearchResult> results = performSearch(baseUrl, query, maxResults);
                if (!results.isEmpty()) {
                    return results;
                }
            } catch (Exception e) {
                continue;
            }
        }
        throw new RuntimeException("All DuckDuckGo search backends failed");
    }

    private List<DuckDuckGoSearchResult> performSearch(String baseUrl, String query, int maxResults)
            throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = baseUrl + encodedQuery;

        Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3000));

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(timeoutMs)
                .followRedirects(true)
                .get();

        return parseResults(doc, maxResults, baseUrl.contains("lite"));
    }

    private String buildUrl(String baseUrl, String region, String safeSearch, String timeLimit) {
        StringBuilder url = new StringBuilder(baseUrl);

        if (region != null && !region.isEmpty() && !region.equals("wt-wt")) {
            url.append("&kl=").append(region);
        }
        if (safeSearch != null && !safeSearch.isEmpty() && !safeSearch.equals("moderate")) {
            url.append("&safe=").append(safeSearch);
        }
        if (timeLimit != null && !timeLimit.isEmpty()) {
            url.append("&df=").append(timeLimit);
        }

        return url.toString();
    }

    private List<DuckDuckGoSearchResult> parseResults(Document doc, int maxResults, boolean isLite) {
        List<DuckDuckGoSearchResult> results = new ArrayList<>();

        if (isLite) {
            return parseLiteVersion(doc, maxResults);
        }

        Elements titleElements = doc.select(".result__title a");

        for (int i = 0; i < titleElements.size() && results.size() < maxResults; i++) {
            Element titleElement = titleElements.get(i);
            String title = titleElement.text().trim();
            String href = titleElement.attr("href");

            String snippet = "";
            try {
                Element parent = titleElement.closest(".result__body, .result");
                if (parent != null) {
                    Element snippetElement = parent.selectFirst(".result__snippet");
                    if (snippetElement != null) {
                        snippet = snippetElement.text().trim();
                    }
                }
            } catch (Exception e) {
                snippet = "";
            }

            if (!title.isEmpty() && !href.isEmpty() && isValidUrl(href)) {
                String cleanUrl = cleanUrl(href);
                results.add(new DuckDuckGoSearchResult(title, cleanUrl, snippet));
            }
        }

        if (results.isEmpty()) {
            Elements altResults = doc.select("div.result, div[data-result]");
            for (Element result : altResults) {
                if (results.size() >= maxResults) break;

                Element titleLink = result.selectFirst("h3 a, h2 a");
                if (titleLink == null) continue;

                String title = titleLink.text().trim();
                String href = titleLink.attr("href");

                Element snippetEl = result.selectFirst(".result-snippet, .snippet");
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";

                if (!title.isEmpty() && !href.isEmpty() && isValidUrl(href)) {
                    String cleanUrl = cleanUrl(href);
                    results.add(new DuckDuckGoSearchResult(title, cleanUrl, snippet));
                }
            }
        }

        return results;
    }

    private List<DuckDuckGoSearchResult> parseLiteVersion(Document doc, int maxResults) {
        List<DuckDuckGoSearchResult> results = new ArrayList<>();

        Elements rows = doc.select("table tr");
        for (Element row : rows) {
            if (results.size() >= maxResults) break;

            Element link = row.selectFirst("td a");
            if (link == null) continue;

            String title = link.text().trim();
            String href = link.attr("href");

            String rowText = row.text().trim();
            String snippet = rowText.replace(title, "").trim();
            if (snippet.startsWith("- ")) snippet = snippet.substring(2);
            if (snippet.length() > 200) snippet = snippet.substring(0, 197) + "...";

            if (!title.isEmpty() && !href.isEmpty() && isValidUrl(href)) {
                String cleanUrl = cleanUrl(href);
                results.add(new DuckDuckGoSearchResult(title, cleanUrl, snippet));
            }
        }

        return results;
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;

        if (url.contains("y.js") ||
                url.contains("duckduckgo.com/y.js") ||
                url.contains("duckduckgo.com/d.js") ||
                url.startsWith("/") && !url.startsWith("//")) {
            return false;
        }

        return url.startsWith("http") || url.startsWith("//");
    }

    private String cleanUrl(String url) {
        if (url == null) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return "https://duckduckgo.com" + url;
        return url;
    }

    public static class DuckDuckGoClientBuilder {
        private Duration timeout;

        DuckDuckGoClientBuilder() {
        }

        public DuckDuckGoClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public DuckDuckGoClient build() {
            return new DuckDuckGoClient(timeout != null ? timeout : Duration.ofSeconds(30));
        }

        public String toString() {
            return "DuckDuckGoClient.DuckDuckGoClientBuilder(timeout=" + this.timeout + ")";
        }
    }
}
