package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiExecutableCode {
    private GeminiLanguage programmingLanguage = GeminiLanguage.PYTHON;
    private String code;

    @JsonCreator
    GeminiExecutableCode(@JsonProperty("programmingLanguage") GeminiLanguage programmingLanguage, @JsonProperty("code") String code) {
        this.programmingLanguage = programmingLanguage;
        this.code = code;
    }

    public static GeminiExecutableCodeBuilder builder() {
        return new GeminiExecutableCodeBuilder();
    }

    public GeminiLanguage getProgrammingLanguage() {
        return this.programmingLanguage;
    }

    public String getCode() {
        return this.code;
    }

    public void setProgrammingLanguage(GeminiLanguage programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiExecutableCode)) return false;
        final GeminiExecutableCode other = (GeminiExecutableCode) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$programmingLanguage = this.getProgrammingLanguage();
        final Object other$programmingLanguage = other.getProgrammingLanguage();
        if (this$programmingLanguage == null ? other$programmingLanguage != null : !this$programmingLanguage.equals(other$programmingLanguage))
            return false;
        final Object this$code = this.getCode();
        final Object other$code = other.getCode();
        if (this$code == null ? other$code != null : !this$code.equals(other$code)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiExecutableCode;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $programmingLanguage = this.getProgrammingLanguage();
        result = result * PRIME + ($programmingLanguage == null ? 43 : $programmingLanguage.hashCode());
        final Object $code = this.getCode();
        result = result * PRIME + ($code == null ? 43 : $code.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiExecutableCode(programmingLanguage=" + this.getProgrammingLanguage() + ", code=" + this.getCode() + ")";
    }

    public static class GeminiExecutableCodeBuilder {
        private GeminiLanguage programmingLanguage;
        private String code;

        GeminiExecutableCodeBuilder() {
        }

        public GeminiExecutableCodeBuilder programmingLanguage(GeminiLanguage programmingLanguage) {
            this.programmingLanguage = programmingLanguage;
            return this;
        }

        public GeminiExecutableCodeBuilder code(String code) {
            this.code = code;
            return this;
        }

        public GeminiExecutableCode build() {
            return new GeminiExecutableCode(this.programmingLanguage, this.code);
        }

        public String toString() {
            return "GeminiExecutableCode.GeminiExecutableCodeBuilder(programmingLanguage=" + this.programmingLanguage + ", code=" + this.code + ")";
        }
    }
}
