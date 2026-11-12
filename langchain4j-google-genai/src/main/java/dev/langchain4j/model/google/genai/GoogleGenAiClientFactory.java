package dev.langchain4j.model.google.genai;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class GoogleGenAiClientFactory {

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
            HttpRetryOptions retryOptions) {

        HttpOptions.Builder httpOptions = HttpOptions.builder();

        Map<String, String> headers = new HashMap<>();
        if (customHeaders != null) {
            headers.putAll(customHeaders);
        }
        // ApiClient stores its default header under lowercase "user-agent" and merges custom "user-agent" values into
        // it; preserve any caller-supplied User-Agent header regardless of casing.
        boolean hasUserAgent = headers.keySet().stream().anyMatch(k -> "user-agent".equalsIgnoreCase(k));
        if (!hasUserAgent) {
            headers.put("user-agent", "LangChain4j");
        }
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
        if (!isVertex) {
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

    private GoogleGenAiClientFactory() {}
}
