package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

@JsonDeserialize(builder = InputAudio.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InputAudio {

    private final String data;
    private final String format;

    public InputAudio(Builder builder) {
        data = builder.data;
        format = builder.format;
    }

    public String getData() {
        return data;
    }

    public String getFormat() {
        return format;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof InputAudio
                && equalTo((InputAudio) another);
    }

    private boolean equalTo(InputAudio another) {
        return Objects.equals(data, another.data)
                && Objects.equals(format, another.format);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(data);
        h += (h << 5) + Objects.hashCode(format);
        return h;
    }

    @Override
    public String toString() {
        return "InputAudio{" +
                "data=" + data +
                ", format=" + format +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        
        private String data;
        private String format;

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public static Builder builder() {
            return new Builder();
        }

        public InputAudio build() {
            return new InputAudio(this);
        }
        
    }
}
