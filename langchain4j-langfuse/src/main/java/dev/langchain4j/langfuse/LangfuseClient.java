package dev.langchain4j.langfuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.langfuse.model.Observation;
import dev.langchain4j.langfuse.model.Score;
import dev.langchain4j.langfuse.model.Session;
import dev.langchain4j.langfuse.model.Trace;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LangfuseClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LangfuseClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final HttpClient httpClient;
    private final String endpoint;
    private final String publicKey;
    private final String secretKey;

    private LangfuseClient(Builder builder) {
        this.endpoint = builder.endpoint;
        this.publicKey = builder.publicKey;
        this.secretKey = builder.secretKey;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : new JdkHttpClientBuilder()
                        .readTimeout(Duration.ofSeconds(30))
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();
    }

    private HttpRequest.Builder baseRequestBuilder() {
        return HttpRequest.builder()
                .addHeader("Authorization", "Bearer " + secretKey)
                .addHeader("Content-Type", "application/json");
    }

    public TraceAPI trace() {
        return new TraceAPI();
    }

    public ObservationAPI observation() {
        return new ObservationAPI();
    }

    public SessionAPI session() {
        return new SessionAPI();
    }

    public ScoreAPI score() {
        return new ScoreAPI();
    }

    public class TraceAPI {
        public void create(Trace trace) throws IOException {
            HttpRequest request = baseRequestBuilder()
                    .method(HttpMethod.POST)
                    .url(endpoint + "/api/public/traces")
                    .body(MAPPER.writeValueAsString(trace))
                    .build();

            SuccessfulHttpResponse response = httpClient.execute(request);
            if (response.statusCode() >= 300) {
                throw new IOException("Failed to create trace: " + response.body());
            }
        }

        public void update(String traceId, Map<String, Object> output, String status) throws IOException {
            HttpRequest request = baseRequestBuilder()
                    .method(HttpMethod.POST)
                    .url(endpoint + "/api/public/traces/" + traceId)
                    .body(MAPPER.writeValueAsString(Map.of(
                            "outputs", output,
                            "status", status)))
                    .build();

            SuccessfulHttpResponse response = httpClient.execute(request);
            if (response.statusCode() >= 300) {
                throw new IOException("Failed to update trace: " + response.body());
            }
        }
    }

    public class ObservationAPI {
        public void createBatch(List<Observation> observations) throws IOException {
            HttpRequest request = baseRequestBuilder()
                    .method(HttpMethod.POST)
                    .url(endpoint + "/api/public/observations/batch")
                    .body(MAPPER.writeValueAsString(observations))
                    .build();

            SuccessfulHttpResponse response = httpClient.execute(request);
            if (response.statusCode() >= 300) {
                throw new IOException("Failed to create observations batch: " + response.body());
            }
        }

        public void update(String observationId, Map<String, Object> output, String status) throws IOException {
            HttpRequest request = baseRequestBuilder()
                    .method(HttpMethod.POST)
                    .url(endpoint + "/api/public/observations/" + observationId)
                    .body(MAPPER.writeValueAsString(Map.of(
                            "output", output,
                            "status", status)))
                    .build();

            SuccessfulHttpResponse response = httpClient.execute(request);
            if (response.statusCode() >= 300) {
                throw new IOException("Failed to update observation: " + response.body());
            }
        }
    }

    public class SessionAPI {
        public void create(Session session) throws IOException {
            HttpRequest request = baseRequestBuilder()
                    .method(HttpMethod.POST)
                    .url(endpoint + "/api/public/sessions")
                    .body(MAPPER.writeValueAsString(session))
                    .build();

            SuccessfulHttpResponse response = httpClient.execute(request);
            if (response.statusCode() >= 300) {
                throw new IOException("Failed to create session: " + response.body());
            }
        }

        public void addTrace(String sessionId, String traceId) throws IOException {
            HttpRequest request = baseRequestBuilder()
                    .method(HttpMethod.POST)
                    .url(endpoint + "/api/public/sessions/" + sessionId + "/traces")
                    .body(MAPPER.writeValueAsString(Map.of("traceId", traceId)))
                    .build();

            SuccessfulHttpResponse response = httpClient.execute(request);
            if (response.statusCode() >= 300) {
                throw new IOException("Failed to add trace to session: " + response.body());
            }
        }
    }

    public class ScoreAPI {
        public void create(Score score) throws IOException {
            HttpRequest request = baseRequestBuilder()
                    .method(HttpMethod.POST)
                    .url(endpoint + "/api/public/scores")
                    .body(MAPPER.writeValueAsString(score))
                    .build();

            SuccessfulHttpResponse response = httpClient.execute(request);
            if (response.statusCode() >= 300) {
                throw new IOException("Failed to create score: " + response.body());
            }
        }
    }

    @Override
    public void close() {
        if (httpClient instanceof AutoCloseable) {
            try {
                ((AutoCloseable) httpClient).close();
            } catch (Exception e) {
                log.warn("Error closing HTTP client", e);
            }
        }
    }

    public void shutdown() {
        close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint = "https://cloud.langfuse.com";
        private String publicKey;
        private String secretKey;
        private HttpClient httpClient;

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public LangfuseClient build() {
            return new LangfuseClient(this);
        }
    }
}
