package dev.langchain4j.langfuse;

import static java.net.http.HttpRequest.BodyPublishers.ofString;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.langfuse.model.Observation;
import dev.langchain4j.langfuse.model.Score;
import dev.langchain4j.langfuse.model.Session;
import dev.langchain4j.langfuse.model.Trace;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LangfuseClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LangfuseClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String endpoint;
    private final String publicKey;
    private final String secretKey;

    private LangfuseClient(Builder builder) {
        this.endpoint = builder.endpoint;
        this.publicKey = builder.publicKey;
        this.secretKey = builder.secretKey;

        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        log.debug("LangfuseClient initialized with endpoint: {}", endpoint);
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
            log.debug("Creating trace: {}", trace);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/public/traces"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(ofString(MAPPER.writeValueAsString(trace)))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    String errorBody = response.body() != null ? response.body() : "No body";
                    log.error("Failed to create trace. Response: {}, Body: {}", response, errorBody);
                    throw new IOException("Failed to create trace: " + response);
                }
                log.debug("Trace created successfully: {}", trace.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to create trace", e);
            }
        }

        public void update(String traceId, Map<String, Object> output, String status) throws IOException {
            log.debug("Updating trace {}. Output: {}, Status: {}", traceId, output, status);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/public/traces/" + traceId))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .method(
                            "PATCH",
                            ofString(MAPPER.writeValueAsString(Map.of(
                                    "outputs", output,
                                    "status", status))))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    String errorBody = response.body() != null ? response.body() : "No body";
                    log.error("Failed to update trace {}. Response: {}, Body: {}", traceId, response, errorBody);
                    throw new IOException("Failed to update trace: " + response);
                }
                log.debug("Trace {} updated successfully", traceId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to update trace", e);
            }
        }
    }

    public class ObservationAPI {
        public void createBatch(List<Observation> observations) throws IOException {
            log.debug("Creating batch of {} observations", observations.size());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/public/observations/batch"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(ofString(MAPPER.writeValueAsString(observations)))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    String errorBody = response.body() != null ? response.body() : "No body";
                    log.error("Failed to create observations batch. Response: {}, Body: {}", response, errorBody);
                    throw new IOException("Failed to create observations batch: " + response);
                }
                log.debug("Batch of {} observations created successfully", observations.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to create observations batch", e);
            }
        }

        public void update(String observationId, Map<String, Object> output, String status) throws IOException {
            log.debug("Updating observation {}. Output: {}, Status: {}", observationId, output, status);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/public/observations/" + observationId))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .method(
                            "PATCH",
                            ofString(MAPPER.writeValueAsString(Map.of(
                                    "output", output,
                                    "status", status))))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    String errorBody = response.body() != null ? response.body() : "No body";
                    log.error(
                            "Failed to update observation {}. Response: {}, Body: {}",
                            observationId,
                            response,
                            errorBody);
                    throw new IOException("Failed to update observation: " + response);
                }
                log.debug("Observation {} updated successfully", observationId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to update observation", e);
            }
        }
    }

    public class SessionAPI {
        public void create(Session session) throws IOException {
            log.debug("Creating session: {}", session);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/public/sessions"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(ofString(MAPPER.writeValueAsString(session)))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    String errorBody = response.body() != null ? response.body() : "No body";
                    log.error("Failed to create session. Response: {}, Body: {}", response, errorBody);
                    throw new IOException("Failed to create session: " + response);
                }
                log.debug("Session created successfully: {}", session.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to create session", e);
            }
        }

        public void addTrace(String sessionId, String traceId) throws IOException {
            log.debug("Adding trace {} to session {}", traceId, sessionId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/public/sessions/" + sessionId + "/traces"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(ofString(MAPPER.writeValueAsString(Map.of("traceId", traceId))))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    String errorBody = response.body() != null ? response.body() : "No body";
                    log.error(
                            "Failed to add trace {} to session {}. Response: {}, Body: {}",
                            traceId,
                            sessionId,
                            response,
                            errorBody);
                    throw new IOException("Failed to add trace to session: " + response);
                }
                log.debug("Trace {} added to session {} successfully", traceId, sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to add trace to session", e);
            }
        }
    }

    public class ScoreAPI {
        public void create(Score score) throws IOException {
            log.debug("Creating score: {}", score);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/public/scores"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(ofString(MAPPER.writeValueAsString(score)))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    String errorBody = response.body() != null ? response.body() : "No body";
                    log.error("Failed to create score. Response: {}, Body: {}", response, errorBody);
                    throw new IOException("Failed to create score: " + response);
                }
                log.debug("Score created successfully: {}", score.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to create score", e);
            }
        }
    }

    @Override
    public void close() {
        log.debug("LangfuseClient closed");
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

        public LangfuseClient build() {
            return new LangfuseClient(this);
        }
    }
}
