package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModelDetails {

    private String format;
    private String family;
    private List<String> families;
    private String parameterSize;
    private String quantizationLevel;

    OllamaModelDetails() {
    }

    OllamaModelDetails(String format, String family, List<String> families, String parameterSize, String quantizationLevel) {
        this.format = format;
        this.family = family;
        this.families = families;
        this.parameterSize = parameterSize;
        this.quantizationLevel = quantizationLevel;
    }

    static Builder builder() {
        return new Builder();
    }

    String getFormat() {
        return format;
    }

    void setFormat(String format) {
        this.format = format;
    }

    String getFamily() {
        return family;
    }

    void setFamily(String family) {
        this.family = family;
    }

    List<String> getFamilies() {
        return families;
    }

    void setFamilies(List<String> families) {
        this.families = families;
    }

    String getParameterSize() {
        return parameterSize;
    }

    void setParameterSize(String parameterSize) {
        this.parameterSize = parameterSize;
    }

    String getQuantizationLevel() {
        return quantizationLevel;
    }

    void setQuantizationLevel(String quantizationLevel) {
        this.quantizationLevel = quantizationLevel;
    }

    static class Builder {

        private String format;
        private String family;
        private List<String> families;
        private String parameterSize;
        private String quantizationLevel;

        Builder format(String format) {
            this.format = format;
            return this;
        }

        Builder family(String family) {
            this.family = family;
            return this;
        }

        Builder families(List<String> families) {
            this.families = families;
            return this;
        }

        Builder parameterSize(String parameterSize) {
            this.parameterSize = parameterSize;
            return this;
        }

        Builder quantizationLevel(String quantizationLevel) {
            this.quantizationLevel = quantizationLevel;
            return this;
        }

        OllamaModelDetails build() {
            return new OllamaModelDetails(format, family, families, parameterSize, quantizationLevel);
        }
    }
}
