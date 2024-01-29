package dev.langchain4j.model.zhipu.chat;

import java.util.*;


public final class Parameters {

    private final String type;
    private final Map<String, Map<String, Object>> properties;
    private final List<String> required;

    private Parameters(Builder builder) {
        this.type = "object";
        this.properties = builder.properties;
        this.required = builder.required;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String type() {
        return "object";
    }

    public Map<String, Map<String, Object>> properties() {
        return this.properties;
    }

    public List<String> required() {
        return this.required;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Parameters && this.equalTo((Parameters) another);
        }
    }

    private boolean equalTo(Parameters another) {
        return Objects.equals(this.properties, another.properties) && Objects.equals(this.required, another.required);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode("object");
        h += (h << 5) + Objects.hashCode(this.properties);
        h += (h << 5) + Objects.hashCode(this.required);
        return h;
    }

    @Override
    public String toString() {
        return "Parameters{"
                + "type=object"
                + ", properties=" + this.properties
                + ", required=" + this.required
                + "}";
    }

    public static final class Builder {
        private Map<String, Map<String, Object>> properties;
        private List<String> required;

        private Builder() {
            this.properties = new HashMap<>();
            this.required = new ArrayList<>();
        }

        public Builder properties(Map<String, Map<String, Object>> properties) {
            if (properties != null) {
                this.properties = Collections.unmodifiableMap(properties);
            }

            return this;
        }

        public Builder required(List<String> required) {
            if (required != null) {
                this.required = Collections.unmodifiableList(required);
            }

            return this;
        }

        public Parameters build() {
            return new Parameters(this);
        }
    }
}