package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

public class Audio {

    private final String prompt;
    private final String base64Data;
    private final String mimeType;

    /**
     * Create a new {@link Audio} from the Builder.
     *
     * @param builder the builder.
     */
    private Audio(Builder builder) {
        this.base64Data = builder.base64Data;
        this.mimeType = builder.mimeType;
        this.prompt = builder.prompt;
    }

    /**
     * Create a new {@link Builder}.
     *
     * @return the new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the base64 data of the audio.
     *
     * @return the base64 data of the audio, or null if not set.
     */
    public String base64Data() {
        return base64Data;
    }

    /**
     * Get the prompt of the audio.
     *
     * @return the prompt, or null if not set.
     */
    public String prompt() {
        return prompt;
    }

    /**
     * Get the mime type of the audio.
     *
     * @return the mime type of the audio, or null if not set.
     */
    public String mimeType() {
        return mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Audio that = (Audio) o;
        return Objects.equals(this.base64Data, that.base64Data)
                && Objects.equals(this.mimeType, that.mimeType)
                && Objects.equals(this.prompt, that.prompt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base64Data, mimeType, prompt);
    }

    @Override
    public String toString() {
        return "Audio {" + " base64Data = "
                + quoted(base64Data) + ", mimeType = "
                + quoted(mimeType) + ", prompt = "
                + quoted(prompt) + " }";
    }

    /**
     * Builder for {@link Audio}.
     */
    public static class Builder {

        private String prompt;
        private String base64Data;
        private String mimeType;

        /**
         * Create a new {@link Builder}.
         */
        public Builder() {}

        /**
         * Set the base64 data of the audio.
         *
         * @param base64Data the base64 data of the audio.
         * @return {@code this}
         */
        public Builder base64Data(String base64Data) {
            this.base64Data = base64Data;
            return this;
        }

        /**
         * Set the mime type of the audio.
         *
         * @param mimeType the mime type of the audio.
         * @return {@code this}
         */
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Set the prompt.
         *
         * @param prompt the prompt.
         * @return {@code this}
         */
        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Build the {@link Audio}.
         *
         * @return the {@link Audio}.
         */
        public Audio build() {
            return new Audio(this);
        }
    }
}
