package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class PineconeNamespaceHelper {

    private static final Logger log = LoggerFactory.getLogger(PineconeNamespaceHelper.class);

    static void deleteNamespace(String apiKey, String indexName, String namespace) {
        try {
            Pinecone client = new Pinecone.Builder(apiKey).build();
            String indexHost = client.describeIndex(indexName).getHost();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + indexHost + "/namespaces/" + namespace))
                    .header("Api-Key", apiKey)
                    .header("X-Pinecone-Api-Version", "2025-04")
                    .DELETE()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 202) {
                log.warn("Failed to delete namespace '{}': HTTP {} - {}",
                        namespace, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to delete namespace '{}'", namespace, e);
        }
    }
}
