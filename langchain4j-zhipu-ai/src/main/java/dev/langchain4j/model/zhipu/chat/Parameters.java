package dev.langchain4j.model.zhipu.chat;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@ToString
@EqualsAndHashCode
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