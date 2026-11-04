package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class GoogleGenAiClientFactory {

    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(20);

    private static final Duration MIN_TIMEOUT = Duration.ofMillis(1);
    private static final Duration MAX_TIMEOUT = Duration.ofDays(24);

    static Client createClient(
            String apiKey,
            GoogleCredentials googleCredentials,
            String projectId,
            String location,
            Duration timeout,
            Map<String, String> customHeaders,
            String apiEndpoint) {

        HttpOptions.Builder httpOptions = HttpOptions.builder();

        Map<String, String> headers = new HashMap<>();
        if (customHeaders != null) {
            headers.putAll(customHeaders);
        }
        headers.put("User-Agent", "LangChain4j");
        httpOptions.headers(headers);

        if (timeout != null) {
            if (timeout.compareTo(MIN_TIMEOUT) < 0 || timeout.compareTo(MAX_TIMEOUT) > 0) {
                throw illegalArgument("timeout must be between 1 millisecond and 24 days, but was %s", timeout);
            }
            httpOptions.timeout((int) timeout.toMillis());
        }

        if (apiEndpoint != null && !apiEndpoint.isEmpty()) {
            httpOptions.baseUrl(apiEndpoint);
        }

        Client.Builder clientBuilder = Client.builder().httpOptions(httpOptions.build());

        boolean isVertex = googleCredentials != null || (projectId != null && location != null);

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
