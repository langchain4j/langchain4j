package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiContent {
    private List<GeminiPart> parts;
    private String role;

    public GeminiContent(String role) {
        this.parts = new ArrayList<>();
        this.role = role;
    }

    @JsonCreator
    public GeminiContent(@JsonProperty("parts") List<GeminiPart> parts, @JsonProperty("role") String role) {
        this.parts = parts;
        this.role = role;
    }

    public static GeminiContentBuilder builder() {
        return new GeminiContentBuilder();
    }

    void addPart(GeminiPart part) {
        this.parts.add(part);
    }

    public List<GeminiPart> getParts() {
        return this.parts;
    }

    public String getRole() {
        return this.role;
    }

    public void setParts(List<GeminiPart> parts) {
        this.parts = parts;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiContent)) return false;
        final GeminiContent other = (GeminiContent) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$parts = this.getParts();
        final Object other$parts = other.getParts();
        if (this$parts == null ? other$parts != null : !this$parts.equals(other$parts)) return false;
        final Object this$role = this.getRole();
        final Object other$role = other.getRole();
        if (this$role == null ? other$role != null : !this$role.equals(other$role)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiContent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $parts = this.getParts();
        result = result * PRIME + ($parts == null ? 43 : $parts.hashCode());
        final Object $role = this.getRole();
        result = result * PRIME + ($role == null ? 43 : $role.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiContent(parts=" + this.getParts() + ", role=" + this.getRole() + ")";
    }

    public static class GeminiContentBuilder {
        private List<GeminiPart> parts;
        private String role;

        GeminiContentBuilder() {
        }

        public GeminiContentBuilder parts(List<GeminiPart> parts) {
            this.parts = parts;
            return this;
        }

        public GeminiContentBuilder role(String role) {
            this.role = role;
            return this;
        }

        public GeminiContent build() {
            return new GeminiContent(this.parts, this.role);
        }

        public String toString() {
            return "GeminiContent.GeminiContentBuilder(parts=" + this.parts + ", role=" + this.role + ")";
        }
    }
}
