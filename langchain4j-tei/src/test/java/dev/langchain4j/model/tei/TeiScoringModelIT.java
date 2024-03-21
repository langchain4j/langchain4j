package dev.langchain4j.model.tei;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class TeiScoringModelIT {

    /**
     * Rerank models typically exceed 1 GB in size and need to be downloaded in advance.
     * <p>
     * text-embeddings-inference supports rapid deployment and serviceization of local embedding/rerank models.
     * <p>
     * For example
     * <p>
     * 1. Download the 'bge-reranker-base' model from Hugging Face to the local directory 'data'.
     * <p>
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
    void should_score_single_text() {

        // given
        TeiScoringModel model = TeiScoringModel.builder()
                .baseUrl(System.getenv("TEI_RANK_MODEL_BASE_URL"))
                .build();
        String query = "What is Deep Learning?";
        List<TextSegment> segments = asList(
                TextSegment.from("Deep Learning is not..."),
                TextSegment.from("Deep learning is...")
        );
        // when
        Response<List<Double>> response = model.scoreAll(segments, query);
        System.out.println(response);

        // then
        assertThat(response.content().size()).isEqualTo(2);
    }

}
