package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiFunctionResponse {
    private String name;
    private Map response;

    @JsonCreator
    GeminiFunctionResponse(@JsonProperty("name") String name, @JsonProperty("response") Map response) {
        this.name = name;
        this.response = response;
    }

    public static GeminiFunctionResponseBuilder builder() {
        return new GeminiFunctionResponseBuilder();
    }

    public String getName() {
        return this.name;
    }

    public Map getResponse() {
        return this.response;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setResponse(Map response) {
        this.response = response;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiFunctionResponse)) return false;
        final GeminiFunctionResponse other = (GeminiFunctionResponse) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$response = this.getResponse();
        final Object other$response = other.getResponse();
        if (this$response == null ? other$response != null : !this$response.equals(other$response)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiFunctionResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $response = this.getResponse();
        result = result * PRIME + ($response == null ? 43 : $response.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiFunctionResponse(name=" + this.getName() + ", response=" + this.getResponse() + ")";
    }

    public static class GeminiFunctionResponseBuilder {
        private String name;
        private Map response;

        GeminiFunctionResponseBuilder() {
        }

        public GeminiFunctionResponseBuilder name(String name) {
            this.name = name;
            return this;
        }

        public GeminiFunctionResponseBuilder response(Map response) {
            this.response = response;
            return this;
        }

        public GeminiFunctionResponse build() {
            return new GeminiFunctionResponse(this.name, this.response);
        }

        public String toString() {
            return "GeminiFunctionResponse.GeminiFunctionResponseBuilder(name=" + this.name + ", response=" + this.response + ")";
        }
    }
}
