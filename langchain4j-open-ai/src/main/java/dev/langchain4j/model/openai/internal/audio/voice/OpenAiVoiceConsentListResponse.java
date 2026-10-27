package dev.langchain4j.model.openai.internal.audio.voice;

import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.List;
import java.util.Objects;

/**
 * A page of voice consent recordings.
 * Find description of fields
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/list">here</a>.
 */
@JsonDeserialize(builder = OpenAiVoiceConsentListResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiVoiceConsentListResponse {

    @JsonProperty
    private final List<OpenAiVoiceConsentResponse> data;

    /**
     * Whether there are more consents after this page.
     */
    @JsonProperty
    private final Boolean hasMore;

    /**
     * The object type, which is always list.
     */
    @JsonProperty
    private final String object;

    /**
     * The ID of the first consent of this page, or {@code null}.
     */
    @JsonProperty
    private final String firstId;

    /**
     * The ID of the last consent of this page, or {@code null}.
     * Pass it as {@code after} to fetch the next page.
     */
    @JsonProperty
    private final String lastId;

    @JsonCreator
    public OpenAiVoiceConsentListResponse(Builder builder) {
        this.object = builder.object;
        this.data = builder.data == null ? null : unmodifiableList(builder.data);
        this.firstId = builder.firstId;
        this.lastId = builder.lastId;
        this.hasMore = builder.hasMore;
    }

    public String object() {
        return object;
    }

    public List<OpenAiVoiceConsentResponse> data() {
        return data;
    }

    public String firstId() {
        return firstId;
    }

    public String lastId() {
        return lastId;
    }

    public Boolean hasMore() {
        return hasMore;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof OpenAiVoiceConsentListResponse list
                && Objects.equals(object, list.object)
                && Objects.equals(data, list.data)
                && Objects.equals(firstId, list.firstId)
                && Objects.equals(lastId, list.lastId)
                && Objects.equals(hasMore, list.hasMore);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(object, data, firstId, lastId, hasMore);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "OpenAiVoiceConsentList{"
                + " object = " + quoted(object)
                + ", data = " + data
                + ", firstId = " + quoted(firstId)
                + ", lastId = " + quoted(lastId)
                + ", hasMore = " + hasMore
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class Builder {

        private String object;
        private List<OpenAiVoiceConsentResponse> data;
        private String firstId;
        private String lastId;
        private Boolean hasMore;

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder data(List<OpenAiVoiceConsentResponse> data) {
            this.data = data;
            return this;
        }

        public Builder firstId(String firstId) {
            this.firstId = firstId;
            return this;
        }

        public Builder lastId(String lastId) {
            this.lastId = lastId;
            return this;
        }

        public Builder hasMore(Boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        public OpenAiVoiceConsentListResponse build() {
            return new OpenAiVoiceConsentListResponse(this);
        }
    }
}
