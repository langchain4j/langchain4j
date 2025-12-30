package dev.langchain4j.data.document.loader.confluence;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;

/**
 * Loads Confluence pages via Atlassian Confluence REST API v1.
 *
 * <p>Uses {@code GET /wiki/rest/api/content} (Confluence Cloud) with pagination to fetch all pages.
 */
public class ConfluenceDocumentLoader {

    public static final String METADATA_TITLE = "title";
    public static final String METADATA_CONFLUENCE_ID = "confluence_id";
    public static final String METADATA_SPACE_KEY = "space_key";

    private static final int DEFAULT_LIMIT = 25;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String contentApiUrl;
    private final String uiBaseUrl;
    private final String authorizationHeaderValue;
    private final String spaceKey;

    private ConfluenceDocumentLoader(Builder builder) {
        String baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.contentApiUrl = contentApiUrl(baseUrl);
        this.uiBaseUrl = uiBaseUrlFromApiUrl(this.contentApiUrl);

        String username = ensureNotBlank(builder.username, "username");
        String apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.authorizationHeaderValue = basicAuthHeaderValue(username, apiKey);

        this.spaceKey = blankToNull(builder.spaceKey);

        this.httpClient = firstNotNull("httpClient", builder.httpClient, new JdkHttpClientBuilder().build());
        this.objectMapper = firstNotNull("objectMapper", builder.objectMapper, new ObjectMapper());
    }

    /**
     * Loads all Confluence pages matching the configured filters.
     *
     * <p>Pagination is handled automatically (the Confluence API defaults to 25 results per page).
     */
    public List<Document> loadDocuments() {
        List<Document> documents = new ArrayList<>();

        int start = 0;
        int batchSize;
        String next;
        do {
            JsonNode response = fetchContent(start, DEFAULT_LIMIT);

            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }

            batchSize = results.size();
            for (JsonNode page : results) {
                documents.add(toDocument(page));
            }

            start += batchSize;
            next = response.path("_links").path("next").asText(null);
        } while (batchSize == DEFAULT_LIMIT || !isNullOrBlank(next));

        return documents;
    }

    /**
     * Alias for {@link #loadDocuments()}.
     */
    public List<Document> load() {
        return loadDocuments();
    }

    private JsonNode fetchContent(int start, int limit) {
        URI uri = buildContentUri(start, limit);

        HttpRequest request = HttpRequest.builder()
                .url(uri.toString())
                .method(HttpMethod.GET)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "LangChain4j")
                .addHeader("Authorization", authorizationHeaderValue)
                .build();

        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            return objectMapper.readTree(response.body());
        } catch (HttpException e) {
            throw new RuntimeException(
                    "Confluence API request failed with status code " + e.statusCode() + ": " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Confluence API: " + uri, e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Confluence API request interrupted: " + uri, e);
            }
            throw new RuntimeException("Failed to call Confluence API: " + uri, e);
        }
    }

    private URI buildContentUri(int start, int limit) {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("type", "page");
        queryParams.put("expand", "body.storage");
        queryParams.put("start", String.valueOf(start));
        queryParams.put("limit", String.valueOf(limit));
        if (!isNullOrBlank(spaceKey)) {
            queryParams.put("spaceKey", spaceKey);
        }

        return URI.create(contentApiUrl + "?" + encodeQuery(queryParams));
    }

    private static String encodeQuery(Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private Document toDocument(JsonNode page) {
        String id = page.path("id").asText(null);
        String title = page.path("title").asText(null);
        String bodyHtml = page.path("body").path("storage").path("value").asText("");

        String text = Jsoup.parse(bodyHtml).text();

        String url = null;
        String webui = page.path("_links").path("webui").asText(null);
        if (!isNullOrBlank(uiBaseUrl) && !isNullOrBlank(webui)) {
            url = joinUrl(uiBaseUrl, webui);
        }

        String pageSpaceKey = page.path("space").path("key").asText(null);
        if (isNullOrBlank(pageSpaceKey)) {
            pageSpaceKey = spaceKey;
        }

        Metadata metadata = new Metadata();
        if (!isNullOrBlank(title)) {
            metadata.put(METADATA_TITLE, title);
        }
        if (!isNullOrBlank(url)) {
            metadata.put(Document.URL, url);
        }
        if (!isNullOrBlank(id)) {
            metadata.put(METADATA_CONFLUENCE_ID, id);
        }
        if (!isNullOrBlank(pageSpaceKey)) {
            metadata.put(METADATA_SPACE_KEY, pageSpaceKey);
        }

        return Document.from(text, metadata);
    }

    private static String joinUrl(String base, String path) {
        if (base.endsWith("/") && path.startsWith("/")) {
            return base + path.substring(1);
        } else if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = ensureNotBlank(baseUrl, "baseUrl").trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String contentApiUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);

        if (normalized.endsWith("/wiki/rest/api/content") || normalized.endsWith("/rest/api/content")) {
            return normalized;
        }
        if (normalized.endsWith("/wiki/rest/api") || normalized.endsWith("/rest/api")) {
            return normalized + "/content";
        }
        if (normalized.endsWith("/wiki")) {
            return normalized + "/rest/api/content";
        }

        return normalized + "/wiki/rest/api/content";
    }

    private static String uiBaseUrlFromApiUrl(String apiUrl) {
        URI uri = URI.create(apiUrl);

        String scheme = uri.getScheme();
        String authority = uri.getAuthority();
        String path = uri.getPath() != null ? uri.getPath() : "";

        int restApiIndex = path.indexOf("/rest/api/");
        if (restApiIndex >= 0) {
            path = path.substring(0, restApiIndex);
        }

        if (!path.isEmpty() && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (scheme == null || authority == null) {
            throw new IllegalArgumentException("Invalid Confluence baseUrl: " + apiUrl);
        }

        return scheme + "://" + authority + path;
    }

    private static String basicAuthHeaderValue(String username, String apiKey) {
        String raw = ensureNotBlank(username, "username") + ":" + ensureNotBlank(apiKey, "apiKey");
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private static String blankToNull(String value) {
        return isNullOrBlank(value) ? null : value.trim();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String username;
        private String apiKey;
        private String spaceKey;

        private HttpClient httpClient;
        private ObjectMapper objectMapper;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Alias for {@link #apiKey(String)}.
         */
        public Builder password(String password) {
            this.apiKey = password;
            return this;
        }

        public Builder spaceKey(String spaceKey) {
            this.spaceKey = spaceKey;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public ConfluenceDocumentLoader build() {
            return new ConfluenceDocumentLoader(this);
        }
    }
}
