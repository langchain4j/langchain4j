package dev.langchain4j.data.document.loader.gitlab;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.source.gitlab.GitLabSource;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads documents from a GitLab repository using GitLab REST API v4.
 *
 * <p>Authentication: Personal Access Token via {@code PRIVATE-TOKEN} header.
 *
 * <p>Uses:
 *
 * <ul>
 *   <li>{@code GET /projects/:id/repository/tree}</li>
 *   <li>{@code GET /projects/:id/repository/files/:file_path/raw}</li>
 * </ul>
 */
public class GitLabDocumentLoader {

    public static final String METADATA_GITLAB_PROJECT_ID = "gitlab_project_id";

    private static final Logger logger = LoggerFactory.getLogger(GitLabDocumentLoader.class);

    private static final String DEFAULT_BASE_URL = "https://gitlab.com";
    private static final int DEFAULT_PER_PAGE = 100;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String apiBaseUrl;
    private final String projectId;
    private final String encodedProjectId;
    private final String projectWebUrl;
    private final String personalAccessToken;

    private GitLabDocumentLoader(Builder builder) {
        String baseUrl = firstNotNull("baseUrl", blankToNull(builder.baseUrl), DEFAULT_BASE_URL);
        this.apiBaseUrl = apiBaseUrl(baseUrl);

        this.projectId = ensureNotBlank(builder.projectId, "projectId").trim();
        this.encodedProjectId = encodePathSegment(this.projectId);
        this.projectWebUrl = projectWebUrl(webBaseUrl(baseUrl), this.projectId);

        this.personalAccessToken = ensureNotBlank(builder.personalAccessToken, "personalAccessToken");

        this.httpClient = firstNotNull("httpClient", builder.httpClient, HttpClient.newHttpClient());
        this.objectMapper = firstNotNull("objectMapper", builder.objectMapper, new ObjectMapper());
    }

    /**
     * Loads a single file from the repository.
     *
     * <p>If {@code ref} is not provided, it will try {@code main} and then {@code master}.
     */
    public Document loadDocument(String filePath, DocumentParser parser) {
        return loadDocument(null, filePath, parser);
    }

    /**
     * Loads a single file from the repository.
     *
     * @param ref the branch/tag/commit SHA to load from. If blank, it will try {@code main} and then {@code master}.
     */
    public Document loadDocument(String ref, String filePath, DocumentParser parser) {
        ensureNotBlank(filePath, "filePath");
        ensureNotNull(parser, "parser");

        TreeItem item = new TreeItem("blob", normalizePath(filePath));

        RuntimeException lastException = null;
        for (String candidateRef : resolveRefCandidates(ref)) {
            try {
                return loadFile(candidateRef, item, parser);
            } catch (RuntimeException e) {
                lastException = e;
            }
        }

        throw ensureNotNull(lastException, "lastException");
    }

    /**
     * Loads documents from a directory.
     *
     * @param ref       branch/tag/commit SHA. If blank, it will try {@code main} and then {@code master}.
     * @param path      optional directory path within the repository. Blank means root.
     * @param recursive whether to traverse sub-directories recursively.
     */
    public List<Document> loadDocuments(String ref, String path, boolean recursive, DocumentParser parser) {
        ensureNotNull(parser, "parser");

        String normalizedPath = blankToNull(path);

        List<TreeItem> items = null;
        String resolvedRef = null;
        RuntimeException lastException = null;
        for (String candidateRef : resolveRefCandidates(ref)) {
            try {
                items = listRepositoryTree(candidateRef, normalizedPath, recursive);
                resolvedRef = candidateRef;
                break;
            } catch (RuntimeException e) {
                lastException = e;
            }
        }

        if (items == null || resolvedRef == null) {
            throw ensureNotNull(lastException, "lastException");
        }

        List<Document> documents = new ArrayList<>();
        for (TreeItem item : items) {
            if (!"blob".equals(item.type)) {
                continue;
            }
            try {
                documents.add(loadFile(resolvedRef, item, parser));
            } catch (RuntimeException e) {
                logger.error("Failed to read document from GitLab: {}/{}", projectId, item.path, e);
            }
        }

        return documents;
    }

    /**
     * Loads all documents from the repository recursively.
     *
     * <p>If {@code ref} is not provided, it will try {@code main} and then {@code master}.
     */
    public List<Document> loadDocuments(DocumentParser parser) {
        return loadDocuments(null, null, true, parser);
    }

    public static Builder builder() {
        return new Builder();
    }

    private Document loadFile(String ref, TreeItem item, DocumentParser parser) {
        URI uri = buildRawFileUri(ref, item.path);
        logger.info("Loading document from GitLab: {}", uri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(DEFAULT_REQUEST_TIMEOUT)
                .header("User-Agent", "LangChain4j")
                .header("PRIVATE-TOKEN", personalAccessToken)
                .GET()
                .build();

        byte[] bytes;
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                String body = safeBodyAsString(response.body());
                throw new RuntimeException(
                        "GitLab API request failed with status code " + response.statusCode() + ": " + body);
            }
            bytes = response.body();
        } catch (IOException e) {
            throw new RuntimeException("Failed to call GitLab API: " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("GitLab API request interrupted: " + uri, e);
        }

        Metadata metadata = new Metadata();
        metadata.put(METADATA_GITLAB_PROJECT_ID, projectId);
        metadata.put(Document.FILE_NAME, fileName(item.path));

        String url = buildWebUrl(projectWebUrl, ref, item.path);
        if (!isNullOrBlank(url)) {
            metadata.put(Document.URL, url);
        }

        return DocumentLoader.load(new GitLabSource(bytes, metadata), parser);
    }

    private List<TreeItem> listRepositoryTree(String ref, String path, boolean recursive) {
        int page = 1;
        List<TreeItem> items = new ArrayList<>();

        while (true) {
            URI uri = buildRepositoryTreeUri(ref, path, recursive, page, DEFAULT_PER_PAGE);
            HttpResponse<String> response = sendJsonRequest(uri);

            JsonNode body;
            try {
                body = objectMapper.readTree(response.body());
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse GitLab API response: " + uri, e);
            }

            if (!body.isArray() || body.isEmpty()) {
                break;
            }

            for (JsonNode node : body) {
                String type = node.path("type").asText(null);
                String itemPath = node.path("path").asText(null);
                if (isNullOrBlank(type) || isNullOrBlank(itemPath)) {
                    continue;
                }
                items.add(new TreeItem(type, itemPath));
            }

            Optional<String> nextPage = response.headers().firstValue("X-Next-Page");
            if (nextPage.isEmpty() || isNullOrBlank(nextPage.get())) {
                break;
            }
            page = Integer.parseInt(nextPage.get());
        }

        return items;
    }

    private HttpResponse<String> sendJsonRequest(URI uri) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(DEFAULT_REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "LangChain4j")
                .header("PRIVATE-TOKEN", personalAccessToken)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException(
                        "GitLab API request failed with status code " + response.statusCode() + ": " + response.body());
            }
            return response;
        } catch (IOException e) {
            throw new RuntimeException("Failed to call GitLab API: " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("GitLab API request interrupted: " + uri, e);
        }
    }

    private URI buildRepositoryTreeUri(String ref, String path, boolean recursive, int page, int perPage) {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("recursive", String.valueOf(recursive));
        queryParams.put("per_page", String.valueOf(perPage));
        queryParams.put("page", String.valueOf(page));
        if (!isNullOrBlank(ref)) {
            queryParams.put("ref", ref);
        }
        if (!isNullOrBlank(path)) {
            queryParams.put("path", path);
        }

        return URI.create(
                apiBaseUrl + "/projects/" + encodedProjectId + "/repository/tree?" + encodeQuery(queryParams));
    }

    private URI buildRawFileUri(String ref, String filePath) {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("ref", ensureNotBlank(ref, "ref"));

        String encodedFilePath = encodePathSegment(normalizePath(filePath));
        return URI.create(apiBaseUrl + "/projects/" + encodedProjectId + "/repository/files/" + encodedFilePath
                + "/raw?" + encodeQuery(queryParams));
    }

    private static List<String> resolveRefCandidates(String ref) {
        if (!isNullOrBlank(ref)) {
            return List.of(ref.trim());
        }
        return List.of("main", "master");
    }

    private static String buildWebUrl(String projectWebUrl, String ref, String filePath) {
        if (isNullOrBlank(projectWebUrl)) {
            return null;
        }

        String normalizedProjectWebUrl = projectWebUrl.trim();
        while (normalizedProjectWebUrl.endsWith("/")) {
            normalizedProjectWebUrl = normalizedProjectWebUrl.substring(0, normalizedProjectWebUrl.length() - 1);
        }

        String encodedRef = encodePathSegment(ref);
        String encodedPath = encodePathPreservingSlashes(normalizePath(filePath));
        return normalizedProjectWebUrl + "/-/blob/" + encodedRef + "/" + encodedPath;
    }

    private static String encodePathPreservingSlashes(String path) {
        String normalized = normalizePath(path);
        String[] parts = normalized.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(encodePathSegment(parts[i]));
        }
        return builder.toString();
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

    private static String encodePathSegment(String value) {
        String decoded = decodePercentEncodedPreservingPlus(ensureNotBlank(value, "value"));
        String encoded = URLEncoder.encode(decoded, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20");
    }

    private static String decodePercentEncodedPreservingPlus(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static String apiBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);

        if (normalized.endsWith("/api/v4")) {
            return normalized;
        }
        if (normalized.endsWith("/api")) {
            return normalized + "/v4";
        }
        return normalized + "/api/v4";
    }

    private static String webBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/api/v4")) {
            return normalized.substring(0, normalized.length() - "/api/v4".length());
        }
        if (normalized.endsWith("/api")) {
            return normalized.substring(0, normalized.length() - "/api".length());
        }
        return normalized;
    }

    private static String projectWebUrl(String webBaseUrl, String projectId) {
        // The GitLab API accepts both numeric project IDs and URL-encoded paths (e.g. group%2Fproject),
        // but the web URL should use the decoded project path (e.g. group/project).
        String decodedProjectId = decodePercentEncodedPreservingPlus(
                ensureNotBlank(projectId, "projectId").trim());
        while (decodedProjectId.startsWith("/")) {
            decodedProjectId = decodedProjectId.substring(1);
        }

        if (decodedProjectId.matches("\\d+")) {
            return webBaseUrl + "/projects/" + decodedProjectId;
        }

        return webBaseUrl + "/" + decodedProjectId;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = ensureNotBlank(baseUrl, "baseUrl").trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePath(String path) {
        String normalized = ensureNotBlank(path, "path").trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String fileName(String path) {
        String normalized = normalizePath(path);
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    private static String safeBodyAsString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String blankToNull(String value) {
        return isNullOrBlank(value) ? null : value.trim();
    }

    private record TreeItem(String type, String path) {}

    public static class Builder {

        private String baseUrl;
        private String projectId;
        private String personalAccessToken;

        private HttpClient httpClient;
        private ObjectMapper objectMapper;

        /**
         * Base URL of GitLab instance, e.g. {@code https://gitlab.com} (default) or a self-hosted URL.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Project ID or URL-encoded path (e.g. {@code 123} or {@code group%2Fproject}).
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Personal access token used as {@code PRIVATE-TOKEN}.
         */
        public Builder personalAccessToken(String personalAccessToken) {
            this.personalAccessToken = personalAccessToken;
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

        public GitLabDocumentLoader build() {
            return new GitLabDocumentLoader(this);
        }
    }
}
