package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = PdfFile.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PdfFile {

    @JsonProperty("file_data")
    private final String fileData;

    @JsonProperty("filename")
    private final String filename;

    public PdfFile(Builder builder) {
        this.fileData = builder.fileData;
        this.filename = builder.filename;
    }

    public String getFileData() {
        return fileData;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof PdfFile
                && equalTo((PdfFile) another);
    }

    private boolean equalTo(PdfFile another) {
        return Objects.equals(fileData, another.fileData)
                && Objects.equals(filename, another.filename);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(fileData);
        h += (h << 5) + Objects.hashCode(filename);
        return h;
    }

    @Override
    public String toString() {
        return "PdfFile{" +
                "fileData=" + (fileData != null ? "[PDF DATA]" : "null") +
                ", filename=" + filename +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String fileData;
        private String filename;

        public Builder fileData(String fileData) {
            this.fileData = fileData;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public PdfFile build() {
            return new PdfFile(this);
        }
    }
}
