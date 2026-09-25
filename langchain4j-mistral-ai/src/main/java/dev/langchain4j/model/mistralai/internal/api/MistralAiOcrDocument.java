package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * The document an OCR request refers to.
 *
 * <p>The service either downloads it from the given URL or reads it from a {@code data:} URI carrying
 * the content inline. {@link #type} selects which of the two URL fields is meaningful.</p>
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiOcrDocument {

    public String type;
    public String documentUrl;
    public String imageUrl;

    public static MistralAiOcrDocument documentUrl(String url) {
        MistralAiOcrDocument document = new MistralAiOcrDocument();
        document.type = "document_url";
        document.documentUrl = url;
        return document;
    }

    public static MistralAiOcrDocument imageUrl(String url) {
        MistralAiOcrDocument document = new MistralAiOcrDocument();
        document.type = "image_url";
        document.imageUrl = url;
        return document;
    }
}
