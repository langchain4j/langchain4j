package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiFunctionCall {
    private String name;
    private Map<String, Object> args;

    @JsonCreator
    GeminiFunctionCall(@JsonProperty("name") String name, @JsonProperty("args") Map<String, Object> args) {
        this.name = name;
        this.args = args;
    }

    public static GeminiFunctionCallBuilder builder() {
        return new GeminiFunctionCallBuilder();
    }

    public String getName() {
        return this.name;
    }

    public Map<String, Object> getArgs() {
        return this.args;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiFunctionCall)) return false;
        final GeminiFunctionCall other = (GeminiFunctionCall) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$args = this.getArgs();
        final Object other$args = other.getArgs();
        if (this$args == null ? other$args != null : !this$args.equals(other$args)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiFunctionCall;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $args = this.getArgs();
        result = result * PRIME + ($args == null ? 43 : $args.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiFunctionCall(name=" + this.getName() + ", args=" + this.getArgs() + ")";
    }

    public static class GeminiFunctionCallBuilder {
        private String name;
        private Map<String, Object> args;

        GeminiFunctionCallBuilder() {
        }

        public GeminiFunctionCallBuilder name(String name) {
            this.name = name;
            return this;
        }

        public GeminiFunctionCallBuilder args(Map<String, Object> args) {
            this.args = args;
            return this;
        }

        public GeminiFunctionCall build() {
            return new GeminiFunctionCall(this.name, this.args);
        }

        public String toString() {
            return "GeminiFunctionCall.GeminiFunctionCallBuilder(name=" + this.name + ", args=" + this.args + ")";
        }
    }
}
