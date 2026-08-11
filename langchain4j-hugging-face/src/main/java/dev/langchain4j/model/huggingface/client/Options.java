package dev.langchain4j.model.huggingface.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class Options {

    private final Boolean waitForModel;
    private final Boolean useCache;

    Options(Builder builder) {
        this.waitForModel = builder.waitForModel;
        this.useCache = builder.useCache;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Options
                && equalTo((Options) another);
    }

    private boolean equalTo(Options another) {
        return Objects.equals(waitForModel, another.waitForModel)
                && Objects.equals(useCache, another.useCache);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(waitForModel);
        h += (h << 5) + Objects.hashCode(useCache);
        return h;
    }

    @Override
    public String toString() {
        return "TextGenerationRequest {"
                + " waitForModel = " + waitForModel
                + ", useCache = " + useCache
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Boolean waitForModel = true;
        private Boolean useCache;

        public Builder waitForModel(Boolean waitForModel) {
            if (waitForModel != null) {
                this.waitForModel = waitForModel;
            }
            return this;
        }

        public Builder useCache(Boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public Options build() {
            return new Options(this);
        }
    }
}
