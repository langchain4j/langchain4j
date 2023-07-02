package dev.langchain4j.model.huggingface;

import java.util.Objects;

class Options {

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

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private Boolean waitForModel = true;
        private Boolean useCache;

        Builder waitForModel(Boolean waitForModel) {
            if (waitForModel != null) {
                this.waitForModel = waitForModel;
            }
            return this;
        }

        Builder useCache(Boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        Options build() {
            return new Options(this);
        }
    }
}
