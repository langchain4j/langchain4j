package dev.langchain4j.model.openai.internal.chat;

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

@JsonDeserialize(builder = LogProbs.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LogProbs {

    @JsonProperty
    private final List<LogProb> content;

    @JsonCreator
    public LogProbs(Builder builder) {
        this.content = builder.content == null ? null : unmodifiableList(builder.content);
    }

    public List<LogProb> content() {
        return content;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof LogProbs && equalTo((LogProbs) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(LogProbs another) {
        return Objects.equals(content, another.content);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(content);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "LogProbs{" + "content=" + content + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Builder {

        private List<LogProb> content;

        public Builder content(List<LogProb> content) {
            if (content != null) {
                this.content = content;
            }
            return this;
        }

        public LogProbs build() {
            return new LogProbs(this);
        }
    }
}
