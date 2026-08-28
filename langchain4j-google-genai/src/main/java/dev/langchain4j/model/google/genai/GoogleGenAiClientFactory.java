package dev.langchain4j.model.google.genai;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
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

        HttpOptions.Builder httpOptions = HttpOptions.builder();

        Map<String, String> headers = new HashMap<>();
        if (customHeaders != null) {
            headers.putAll(customHeaders);
        }
        headers.put("User-Agent", "LangChain4j");
        httpOptions.headers(headers);

        if (timeout != null) {
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
        } else if (apiKey != null) {
            clientBuilder.apiKey(apiKey);
        }

        return clientBuilder.build();
    }

    private GoogleGenAiClientFactory() {}
}
