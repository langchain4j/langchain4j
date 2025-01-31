package dev.langchain4j.store.embedding.vespa;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class VespaEmbeddingStoreIT extends EmbeddingStoreWithoutMetadataIT {

    @Container
    static GenericContainer<?> vespa = new GenericContainer<>(DockerImageName.parse("vespaengine/vespa:8.458.13"))
            .waitingFor(Wait.forListeningPorts(19071))
            .withExposedPorts(8080, 19071);

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private final VespaEmbeddingStore embeddingStore = VespaEmbeddingStore.builder()
            .url(String.format("http://%s:%d", vespa.getHost(), vespa.getMappedPort(8080)))
            .build();

    @Test
    void should_find_relevant_matches() {
        // given
        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding1 = embeddingModel.embed(segment1).content();

        TextSegment segment2 = TextSegment.from("I've never been to New York.");
        Embedding embedding2 = embeddingModel.embed(segment2).content();

        TextSegment segment3 =
                TextSegment.from("But actually we tried our new swimming pool yesterday and it was awesome!");
        Embedding embedding3 = embeddingModel.embed(segment3).content();

        TextSegment segment4 = TextSegment.from("John Lennon was a very cool person.");
        Embedding embedding4 = embeddingModel.embed(segment4).content();

        embeddingStore.addAll(
                asList(embedding1, embedding2, embedding3, embedding4), asList(segment1, segment2, segment3, segment4));

        // when
        Embedding sportEmbedding =
                embeddingModel.embed("What is your favorite sport?").content();
        List<EmbeddingMatch<TextSegment>> sportMatches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(sportEmbedding)
                        .maxResults(2)
                        .build())
                .matches();
        Embedding musicEmbedding =
                embeddingModel.embed("And what about musicians?").content();
        List<EmbeddingMatch<TextSegment>> musicMatches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(musicEmbedding)
                        .maxResults(5)
                        .minScore(0.6)
                        .build())
                .matches();

        // then
        assertThat(sportMatches).hasSize(2);
        assertThat(sportMatches.get(0).score()).isCloseTo(0.808, percentage());
        assertThat(sportMatches.get(0).embedded().text()).contains("football");
        assertThat(sportMatches.get(1).score()).isCloseTo(0.606, percentage());
        assertThat(sportMatches.get(1).embedded().text()).contains("swimming pool");

        assertThat(musicMatches).hasSize(1);
        assertThat(musicMatches.get(0).score()).isCloseTo(0.671, percentage());
        assertThat(musicMatches.get(0).embedded().text()).contains("John Lennon");
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }

    @Override
    protected void ensureStoreIsEmpty() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(super::ensureStoreIsEmpty);
    }

    @Override
    protected void ensureStoreIsReady() {
        try {
            getAllEmbeddings();
        } catch (Exception e) {
            deployVespaApp();
        }
    }

    private void deployVespaApp() {
        try {
            URI uri = new URI(String.format(
                    "http://%s:%d/application/v2/tenant/default/prepareandactivate",
                    vespa.getHost(), vespa.getMappedPort(19071)));
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/zip");

            try (InputStream in = VespaEmbeddingStoreIT.class.getResourceAsStream("/vespa_app.zip");
                    DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            assertThat(conn.getResponseCode()).isEqualTo(200);

            // wait for Vespa application is deployed properly
            await().atMost(Duration.ofSeconds(60)).ignoreExceptions().untilAsserted(super::ensureStoreIsEmpty);
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }
}
