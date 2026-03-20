package dev.langchain4j.model.google.genai;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import java.time.Duration;

class GoogleGenAiClientFactory {

    static Client createClient(
            String apiKey, GoogleCredentials googleCredentials, String projectId, String location, Duration timeout) {

        HttpOptions.Builder httpOptions = HttpOptions.builder();
        if (timeout != null) {
            httpOptions.timeout((int) timeout.toMillis());
        }

        Client.Builder clientBuilder = Client.builder().httpOptions(httpOptions.build());

        if (googleCredentials != null) {
            clientBuilder.credentials(googleCredentials);
            clientBuilder.vertexAI(true);
            if (projectId != null) clientBuilder.project(projectId);
            if (location != null) clientBuilder.location(location);
        } else if (apiKey != null) {
            clientBuilder.apiKey(apiKey);
        }

        return clientBuilder.build();
    }

    private GoogleGenAiClientFactory() {}
}
