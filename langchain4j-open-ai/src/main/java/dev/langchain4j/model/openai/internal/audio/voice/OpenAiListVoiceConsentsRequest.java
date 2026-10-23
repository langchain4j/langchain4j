package dev.langchain4j.model.openai.internal.audio.voice;

/**
 * Represents the request to list voice consent recordings.
 * Find description of parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents/methods/list">here</a>.
 */
public class OpenAiListVoiceConsentsRequest {

    /**
     * A cursor for use in pagination: the ID of the last object of the previous page (e.g. the
     * {@code last_id} of the previous {@link OpenAiVoiceConsentListResponse}), to fetch the next page.
     */
    private final String after;

    /**
     * A limit on the number of objects to be returned. Between 1 and 100, defaults to 20.
     */
    private final Integer limit;

    public OpenAiListVoiceConsentsRequest(Builder builder) {
        this.after = builder.after;
        this.limit = builder.limit;
    }

    public String after() {
        return after;
    }

    public Integer limit() {
        return limit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String after;
        private Integer limit;

        public Builder after(String after) {
            this.after = after;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public OpenAiListVoiceConsentsRequest build() {
            return new OpenAiListVoiceConsentsRequest(this);
        }
    }
}
