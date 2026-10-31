package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * A request to the Mistral OCR API ({@code POST /v1/ocr}).
 *
 * <p>Only the fields produced by {@code MistralAiOcrModel} are declared. Properties left {@code null}
 * are omitted so that the service applies its own defaults.</p>
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiOcrRequest {

    public String model;
    public MistralAiOcrDocument document;
    public List<Integer> pages;
    public Boolean extractHeader;
    public Boolean extractFooter;
    public String tableFormat;
}
