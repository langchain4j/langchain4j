package dev.langchain4j.mcp.registryclient;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.mcp.registryclient.model.McpRegistryHealth;
import dev.langchain4j.mcp.registryclient.model.McpRegistryPong;
import dev.langchain4j.mcp.registryclient.model.McpServer;
import dev.langchain4j.mcp.registryclient.model.McpServerList;
import dev.langchain4j.mcp.registryclient.model.McpServerListRequest;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultMcpRegistryClient implements McpRegistryClient {

    private static final String OFFICIAL_REGISTRY_URL = "https://registry.modelcontextprotocol.io";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final SimpleModule JACKSON_MODULE = new SimpleModule("mcp-registry-client-module")
            .addDeserializer(ZonedDateTime.class, new JsonDeserializer<>() {
                @Override
                public ZonedDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    JsonNode node = p.getCodec().readTree(p);
                    return ZonedDateTime.parse(node.asText(), ISO_DATE_TIME);
                }
            });
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setVisibility(FIELD, ANY)
            .registerModule(JACKSON_MODULE)
            .enable(INDENT_OUTPUT);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Supplier<Map<String, String>> headers;

    private DefaultMcpRegistryClient(
            String baseUrl,
            HttpClient httpClient,
            Supplier<Map<String, String>> headers,
            boolean logRequests,
            boolean logResponses) {
        this.baseUrl = Utils.getOrDefault(baseUrl, OFFICIAL_REGISTRY_URL);
        this.headers = Utils.getOrDefault(headers, () -> HashMap::new);
        HttpClient httpClientToUse =
                Utils.getOrDefault(httpClient, () -> HttpClientBuilderLoader.loadHttpClientBuilder()
                        .build());
        if (logRequests || logResponses) {
            this.httpClient = new LoggingHttpClient(httpClientToUse, logRequests, logResponses);
        } else {
            this.httpClient = httpClientToUse;
        }
    }

    @Override
    public McpServerList listServers(McpServerListRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String params = processServerListRequestPathParams(request);
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.GET)
                .url(baseUrl, !params.isEmpty() ? "/v0/servers?" + params : "/v0/servers")
                .addHeaders(currentHeaders())
                .build();
        return sendAndProcessResponse(httpRequest, McpServerList.class);
    }

    @Override
    public McpServer getServerDetails(String id) {
        Objects.requireNonNull(id, "id cannot be null");
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.GET)
                .url(baseUrl, "/v0/servers/" + id)
                .addHeaders(currentHeaders())
                .build();
        return sendAndProcessResponse(httpRequest, McpServer.class);
    }

    @Override
    public McpRegistryHealth healthCheck() {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.GET)
                .url(baseUrl, "/v0/health")
                .addHeaders(currentHeaders())
                .build();
        return sendAndProcessResponse(httpRequest, McpRegistryHealth.class);
    }

    @Override
    public McpRegistryPong ping() {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.GET)
                .url(baseUrl, "/v0/ping")
                .addHeaders(currentHeaders())
                .build();
        return sendAndProcessResponse(httpRequest, McpRegistryPong.class);
    }

    private Map<String, String> currentHeaders() {
        Map<String, String> map = headers.get();
        map.put("Content-Type", "application/json");
        map.put("Accept", "application/json, application/problem+json");
        return map;
    }

    private String processServerListRequestPathParams(McpServerListRequest request) {
        List<String> params = new ArrayList<>();
        if (request.getCursor() != null) {
            params.add("cursor=" + request.getCursor());
        }
        if (request.getLimit() != null) {
            params.add("limit=" + request.getLimit());
        }
        if (request.getSearch() != null) {
            params.add("search=" + request.getSearch());
        }
        if (request.getUpdatedSince() != null) {
            params.add("updatedSince=" + request.getUpdatedSince().format(DATE_TIME_FORMATTER));
        }
        if (request.getVersion() != null) {
            params.add("version=" + request.getVersion());
        }
        return params.stream().collect(Collectors.joining("&"));
    }

    private <T> T sendAndProcessResponse(HttpRequest httpRequest, Class<T> returnType) {
        SuccessfulHttpResponse response = httpClient.execute(httpRequest);
        try {
            return OBJECT_MAPPER.readValue(response.body(), returnType);
        } catch (JsonProcessingException e) {
            throw new McpRegistryClientException(e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl;
        private HttpClient httpClient;
        private Supplier<Map<String, String>> headers;
        private boolean logRequests = false;
        private boolean logResponses = false;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = () -> headers;
            return this;
        }

        public Builder headersSupplier(Supplier<Map<String, String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public DefaultMcpRegistryClient build() {
            return new DefaultMcpRegistryClient(baseUrl, httpClient, headers, logRequests, logResponses);
        }
    }
}
