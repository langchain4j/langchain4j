package dev.langchain4j.agent.tool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

public class ToolExecutionRequest {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final String name;
    private final String arguments;

    private ToolExecutionRequest(Builder builder) {
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    public String name() {
        return name;
    }

    public String arguments() {
        return arguments;
    }

    public Map<String, Object> argumentsAsMap() {
        return GSON.fromJson(arguments, MAP_TYPE);
    }

    public <T> T argument(String name) {
        Map<String, Object> arguments = argumentsAsMap();
        return (T) arguments.get(name);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolExecutionRequest
                && equalTo((ToolExecutionRequest) another);
    }

    private boolean equalTo(ToolExecutionRequest another) {
        return Objects.equals(name, another.name)
                && Objects.equals(arguments, another.arguments);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(arguments);
        return h;
    }

    @Override
    public String toString() {
        return "ToolExecutionRequest {"
                + " name = " + name
                + ", arguments = " + arguments
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String arguments;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public ToolExecutionRequest build() {
            return new ToolExecutionRequest(this);
        }
    }
}
