package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.Objects;

@JsonDeserialize(builder = Content.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Content {

    @JsonProperty
    private final ContentType type;

    @JsonProperty
    private final String text;

    @JsonProperty
    private final ImageUrl imageUrl;

    @JsonProperty
    private final VideoUrl videoUrl;

    @JsonProperty
    private final InputAudio inputAudio;

    @JsonProperty
    private final PdfFile file;

    public Content(Builder builder) {
        this.type = builder.type;
        this.text = builder.text;
        this.imageUrl = builder.imageUrl;
        this.videoUrl = builder.videoUrl;
        this.inputAudio = builder.inputAudio;
        this.file = builder.file;
    }

    public ContentType type() {
        return type;
    }

    public String text() {
        return text;
    }

    public ImageUrl imageUrl() {
        return imageUrl;
    }

    public VideoUrl videoUrl() {
        return videoUrl;
    }

    public InputAudio inputAudio() {
        return inputAudio;
    }

    public PdfFile file() {
        return file;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Content && equalTo((Content) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(Content another) {
        return Objects.equals(type, another.type)
                && Objects.equals(text, another.text)
                && Objects.equals(imageUrl, another.imageUrl)
                && Objects.equals(videoUrl, another.videoUrl)
                && Objects.equals(inputAudio, another.inputAudio)
                && Objects.equals(file, another.file);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(text);
        h += (h << 5) + Objects.hashCode(imageUrl);
        h += (h << 5) + Objects.hashCode(videoUrl);
        h += (h << 5) + Objects.hashCode(inputAudio);
        h += (h << 5) + Objects.hashCode(file);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "Content{" + "type="
                + type + ", text="
                + text + ", imageUrl="
                + imageUrl + ", videoUrl="
                + videoUrl + ", inputAudio="
                + inputAudio + ", file="
                + file + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private ContentType type;
        private String text;
        private ImageUrl imageUrl;
        private VideoUrl videoUrl;
        private InputAudio inputAudio;
        private PdfFile file;

        public Builder type(ContentType type) {
            this.type = type;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder imageUrl(ImageUrl imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder videoUrl(VideoUrl videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public Builder inputAudio(InputAudio inputAudio) {
            this.inputAudio = inputAudio;
            return this;
        }

        public Builder file(PdfFile file) {
            this.file = file;
            return this;
        }

        public Content build() {
            return new Content(this);
        }
    }
}
