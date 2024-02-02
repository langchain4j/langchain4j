package dev.langchain4j.model.qianfan.client.chat;

import java.util.*;

public class Parameters {
    private final String type;
    private final Map<String, Map<String, Object>> properties;
    private final List<String> required;

    private Parameters(Builder builder) {
        this.type = builder.type;
        this.properties = builder.properties;
        this.required = builder.required;
    }

    public String type() {
        return this.type;
    }

    public Map<String, Map<String, Object>> properties() {
        return this.properties;
    }

    public List<String> required() {
        return this.required;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Parameters
                    && this.equalTo((Parameters)another);
        }
    }

    private boolean equalTo(Parameters another) {
        return Objects.equals(this.type, another.type) && Objects.equals(this.properties, another.properties) && Objects.equals(this.required, another.required);
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.type);
        h += (h << 5) + Objects.hashCode(this.properties);
        h += (h << 5) + Objects.hashCode(this.required);
        return h;
    }

    public String toString() {
        return "Parameters{type=" + this.type + ", properties=" + this.properties + ", required=" + this.required + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String type;
        private Map<String, Map<String, Object>> properties;
        private List<String> required;

        private Builder() {
            this.type = "object";
            this.properties = new HashMap();
            this.required = new ArrayList();
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder properties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
            return this;
        }

        public Builder required(List<String> required) {
            this.required = required;
            return this;
        }

        public Parameters build() {
            return new Parameters(this);
        }
    }
}

