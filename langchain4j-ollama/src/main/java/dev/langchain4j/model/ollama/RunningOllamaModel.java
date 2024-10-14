package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RunningOllamaModel {

    private String name;
    private String model;
    private Long size;
    private String digest;
    private OllamaModelDetails details;
    @JsonDeserialize(using = OllamaDateDeserializer.class)
    private OffsetDateTime expiresAt;
    private Long sizeVram;

    RunningOllamaModel() {
    }

    RunningOllamaModel(String name, String model, Long size, OllamaModelDetails details, String digest, OffsetDateTime expiresAt, Long sizeVram) {
        this.name = name;
        this.model = model;
        this.size = size;
        this.details = details;
        this.digest = digest;
        this.expiresAt = expiresAt;
        this.sizeVram = sizeVram;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public OllamaModelDetails getDetails() {
        return details;
    }

    public void setDetails(OllamaModelDetails details) {
        this.details = details;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getSizeVram() {
        return sizeVram;
    }

    public void setSizeVram(Long sizeVram) {
        this.sizeVram = sizeVram;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String name;
        private String model;
        private Long size;
        private String digest;
        private OllamaModelDetails details;
        private OffsetDateTime expiresAt;
        private Long sizeVram;

        Builder name(String name) {
            this.name = name;
            return this;
        }

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder size(Long size) {
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

        Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        Builder sizeVram(Long sizeVram) {
            this.sizeVram = sizeVram;
            return this;
        }

        RunningOllamaModel build() {
            return new RunningOllamaModel(name, model, size, details, digest, expiresAt, sizeVram);
        }
    }
}
