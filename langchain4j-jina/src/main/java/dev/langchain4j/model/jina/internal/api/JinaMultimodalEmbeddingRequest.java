package dev.langchain4j.model.jina.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Multimodal variant of {@link JinaEmbeddingRequest} for models such as {@code jina-clip-v2} and
 * {@code jina-embeddings-v4}: each {@code input} item is a single text or a single image (Jina embeds one
 * modality per item), producing one embedding per item.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JinaMultimodalEmbeddingRequest {

    public String model;
    public List<JinaMultimodalInput> input;

    public JinaMultimodalEmbeddingRequest(String model, List<JinaMultimodalInput> input) {
        this.model = model;
        this.input = input;
    }

    /** One input item: exactly one of {@code text} / {@code image} is populated. */
    @JsonInclude(NON_NULL)
    public static class JinaMultimodalInput {

        public String text;
        public String image;

        private JinaMultimodalInput(String text, String image) {
            this.text = text;
            this.image = image;
        }

        public static JinaMultimodalInput text(String text) {
            return new JinaMultimodalInput(text, null);
        }

        public static JinaMultimodalInput image(String image) {
            return new JinaMultimodalInput(null, image);
        }
    }
}
