package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

class GoogleGenAiClientFactory {

    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(20);

    static Client createClient(
            String apiKey,
            GoogleCredentials googleCredentials,
            String projectId,
            String location,
            Duration timeout,
            Map<String, String> customHeaders,
            String apiEndpoint) {
        return createClient(apiKey, googleCredentials, projectId, location, timeout, customHeaders, apiEndpoint, null);
    }

    static Client createClient(
            String apiKey,
            GoogleCredentials googleCredentials,
            String projectId,
            String location,
            Duration timeout,
            Map<String, String> customHeaders,
            String apiEndpoint,
            @Nullable String apiVersion) {
        return createClient(
                apiKey, googleCredentials, projectId, location, timeout, customHeaders, apiEndpoint, apiVersion, null);
    }

    static Client createClient(
            String apiKey,
            GoogleCredentials googleCredentials,
            String projectId,
            String location,
            Duration timeout,
            Map<String, String> customHeaders,
            String apiEndpoint,
            @Nullable String apiVersion,
            @Nullable HttpRetryOptions retryOptions) {

        HttpOptions.Builder httpOptions = HttpOptions.builder();

        // the SDK appends only a lowercase "user-agent" header to its own User-Agent, other spellings are dropped
        Map<String, String> headers = new HashMap<>();
        String userAgent = "LangChain4j";
        if (customHeaders != null) {
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                if ("user-agent".equalsIgnoreCase(header.getKey())) {
                    userAgent = header.getValue();
                } else {
                    headers.put(header.getKey(), header.getValue());
                }
            }
        }
        headers.put("user-agent", userAgent);
        httpOptions.headers(headers);

        if (timeout != null) {
            httpOptions.timeout((int) timeout.toMillis());
        }

        if (retryOptions != null) {
            httpOptions.retryOptions(retryOptions);
        }

        if (apiEndpoint != null && !apiEndpoint.isEmpty()) {
            httpOptions.baseUrl(apiEndpoint);
        }

        // google-genai 2.x changed the Gemini Developer API default from "v1beta" to "v1"; keep "v1beta" so beta and
        // preview features (caching, files, TTS, decisions, thinking, etc.) continue to work as in 1.x.
        boolean isVertex = googleCredentials != null || (projectId != null && location != null);
        if (apiVersion != null) {
            httpOptions.apiVersion(apiVersion);
        } else if (!isVertex && !isVertexAiOrApiVersionSetIn(System::getenv)) {
            httpOptions.apiVersion("v1beta");
        }

        Client.Builder clientBuilder = Client.builder().httpOptions(httpOptions.build());

        if (isVertex) {
            clientBuilder.vertexAI(true);
            if (googleCredentials != null) {
                clientBuilder.credentials(googleCredentials);
            }
            if (projectId != null) clientBuilder.project(projectId);
            if (location != null) clientBuilder.location(location);
        }

        if (apiKey != null) {
            clientBuilder.apiKey(apiKey);
        }

        return clientBuilder.build();
    }

    /**
     * The SDK also turns Vertex AI on ({@code GOOGLE_GENAI_USE_ENTERPRISE}, {@code GOOGLE_GENAI_USE_VERTEXAI}) and
     * sets the API version ({@code GOOGLE_GENAI_API_VERSION}) from environment variables. Then the API version is
     * left to the SDK.
     */
    static boolean isVertexAiOrApiVersionSetIn(Function<String, String> environment) {
        String enterprise = environment.apply("GOOGLE_GENAI_USE_ENTERPRISE");
        String vertexAi = isNullOrEmpty(enterprise) ? environment.apply("GOOGLE_GENAI_USE_VERTEXAI") : enterprise;
        return "true".equalsIgnoreCase(vertexAi) || !isNullOrEmpty(environment.apply("GOOGLE_GENAI_API_VERSION"));
    }

    private GoogleGenAiClientFactory() {}
}
