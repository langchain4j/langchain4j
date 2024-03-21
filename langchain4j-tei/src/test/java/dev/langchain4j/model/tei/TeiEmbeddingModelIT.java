package dev.langchain4j.model.tei;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.tei.client.ReRankResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Text Embeddings Inference (TEI) is a comprehensive toolkit designed for efficient deployment and serving of open source text embeddings models.
 * <p>
 * It enables high-performance extraction for the most popular models, including FlagEmbedding, Ember, GTE, and E5.
 *
 * @see <a href="https://huggingface.co/docs/text-embeddings-inference/index">Text Embeddings Inference</a>
 */
@Disabled("huggingface text-embeddings-inference may require downloading embedding models and rerank models, which could be slow.")
class TeiEmbeddingModelIT {

    private static EmbeddingModel embeddingModel;

    private static GenericContainer<?> textEmbeddingsContainer;

    @BeforeAll
    public static void setUp() {
        textEmbeddingsContainer = new GenericContainer<>(
                DockerImageName.parse("ghcr.io/huggingface/text-embeddings-inference:cpu-1.1"))
                .withExposedPorts(9005)
                .withEnv("PORT", "9005")
                .withCommand("--model-id", "BAAI/bge-small-en-v1.5")
                .waitingFor(Wait.forHttp("/info")).withStartupTimeout(Duration.ofMinutes(10));
        textEmbeddingsContainer.start();
        embeddingModel = TeiEmbeddingModel.builder()
                .baseUrl(getBaseUrl())
                .build();
    }

    static String getBaseUrl() {
        return "http://" + textEmbeddingsContainer.getHost() + ":" + textEmbeddingsContainer.getMappedPort(9005);
    }

    @AfterAll
    public static void tearDown() {
        if (textEmbeddingsContainer != null) {
            textEmbeddingsContainer.stop();
        }
    }

    @Test
    void should_embed() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = embeddingModel.embed(text);
        System.out.println(response);

        // then
        assertThat(response.content().vector()).isNotEmpty();
        assertThat(response.tokenUsage()).isNotNull();
    }

    /**
     * Rerank models typically exceed 1 GB in size and need to be downloaded in advance.
     * <p>
     * text-embeddings-inference supports rapid deployment and serviceization of local embedding/rerank models.
     * <p>
     * For example
     * <p>
     * 1. Download the 'bge-reranker-base' model from Hugging Face to the local directory 'data'.
     *
     * 2. Execute the Docker command.
     *
     * <pre>{@code
     *
     *  model="./data/bge-reranker-base" && volume="$PWD/data" && docker run -p 9003:9003 -v $volume:/data -e PORT=9003 --pull always ghcr.io/huggingface/text-embeddings-inference:cpu-1.1 --model-id $model
     *
     * }</pre>
     * This command deploys and serves the 'bge-reranker-base' model for text embeddings inference. The model is mounted from the local 'data' directory into the Docker container for deployment and service.
     * <p>
     */
    @Test
    void rerank() {
        // given
        TeiRerankModel model = TeiRerankModel.builder()
                .baseUrl(System.getenv("TEI_RANK_MODEL_BASE_URL"))
                .build();
        String query = "What is Deep Learning?";
        List<TextSegment> segments = asList(
                TextSegment.from("Deep Learning is not..."),
                TextSegment.from("Deep learning is...")
        );
        // when
        Response<List<ReRankResult>> response = model.rerank(query, segments);
        System.out.println(response);

        // then
        assertThat(response.content().size()).isEqualTo(2);

    }

}
