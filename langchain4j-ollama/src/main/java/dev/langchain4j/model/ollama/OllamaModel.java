package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class OllamaModel {

    private String name;
    private long size;
    private String digest;
    private OllamaModelDetails details;

    OllamaModel() {
    }

    OllamaModel(String name, long size, String digest, OllamaModelDetails details) {
        this.name = name;
        this.size = size;
        this.digest = digest;
        this.details = details;
    }

    static Builder builder() {
        return new Builder();
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    long getSize() {
        return size;
    }

    void setSize(long size) {
        this.size = size;
    }

    String getDigest() {
        return digest;
    }

    void setDigest(String digest) {
        this.digest = digest;
    }

    OllamaModelDetails getDetails() {
        return details;
    }

    void setDetails(OllamaModelDetails details) {
        this.details = details;
    }

    static class Builder {

        private String name;
        private long size;
        private String digest;
        private OllamaModelDetails details;

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder size(long size) {
            this.size = size;
            return this;
        }

        Builder digest(String digest) {
            this.digest = digest;
            return this;
        }

        Builder details(OllamaModelDetails details) {
            this.details = details;
            return this;
        }

        OllamaModel build() {
            return new OllamaModel(name, size, digest, details);
        }
    }
}
