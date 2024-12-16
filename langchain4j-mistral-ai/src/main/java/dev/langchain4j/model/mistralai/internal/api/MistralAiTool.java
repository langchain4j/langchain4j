package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiTool {
    private MistralAiToolType type;
    private MistralAiFunction function;

    public static MistralAiTool from(MistralAiFunction function) {
        return MistralAiTool.builder().type(MistralAiToolType.FUNCTION).function(function).build();
    }



    public static class MistralAiToolBuilder {
    
        private MistralAiToolType type;
    
        private MistralAiFunction function;

    
        MistralAiToolBuilder() {
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiTool.MistralAiToolBuilder type(MistralAiToolType type) {
            this.type = type;
            return this;
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiTool.MistralAiToolBuilder function(MistralAiFunction function) {
            this.function = function;
            return this;
        }

    
        public MistralAiTool build() {
            return new MistralAiTool(this.type, this.function);
        }

        public String toString() {
            return "MistralAiTool.MistralAiToolBuilder(type=" + this.type + ", function=" + this.function + ")";
        }
    }


    public static MistralAiTool.MistralAiToolBuilder builder() {
        return new MistralAiTool.MistralAiToolBuilder();
    }


    public MistralAiToolType getType() {
        return this.type;
    }


    public MistralAiFunction getFunction() {
        return this.function;
    }


    public void setType(MistralAiToolType type) {
        this.type = type;
    }


    public void setFunction(MistralAiFunction function) {
        this.function = function;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + Objects.hashCode(this.type);
        hash = 37 * hash + Objects.hashCode(this.function);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiTool other = (MistralAiTool) obj;
        return this.type == other.type
                && Objects.equals(this.function, other.function);
    }

    public String toString() {
        return "MistralAiTool(type=" + this.getType() + ", function=" + this.getFunction() + ")";
    }


    public MistralAiTool() {
    }


    public MistralAiTool(MistralAiToolType type, MistralAiFunction function) {
        this.type = type;
        this.function = function;
    }
}
