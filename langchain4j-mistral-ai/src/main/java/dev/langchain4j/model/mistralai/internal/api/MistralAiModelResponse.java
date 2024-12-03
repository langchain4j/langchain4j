package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiModelResponse {
    private String object;
    private List<MistralAiModelCard> data;



    public static class MistralAiModelResponseBuilder {
    
        private String object;
    
        private List<MistralAiModelCard> data;

    
        MistralAiModelResponseBuilder() {
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiModelResponse.MistralAiModelResponseBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
    
        public MistralAiModelResponse.MistralAiModelResponseBuilder data(List<MistralAiModelCard> data) {
            this.data = data;
            return this;
        }

    
        public MistralAiModelResponse build() {
            return new MistralAiModelResponse(this.object, this.data);
        }

        public String toString() {
            return "MistralAiModelResponse.MistralAiModelResponseBuilder("
                    + "object=" + this.object
                    + ", data=" + this.data
                    + ")";
        }
    }


    public static MistralAiModelResponse.MistralAiModelResponseBuilder builder() {
        return new MistralAiModelResponse.MistralAiModelResponseBuilder();
    }


    public String getObject() {
        return this.object;
    }


    public List<MistralAiModelCard> getData() {
        return this.data;
    }


    public void setObject(String object) {
        this.object = object;
    }


    public void setData(List<MistralAiModelCard> data) {
        this.data = data;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.object);
        hash = 47 * hash + Objects.hashCode(this.data);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiModelResponse other = (MistralAiModelResponse) obj;
        return Objects.equals(this.object, other.object)
                && Objects.equals(this.data, other.data);
    }

    public String toString() {
        return "MistralAiModelResponse("
                + "object=" + this.getObject()
                + ", data=" + this.getData()
                + ")";
    }


    public MistralAiModelResponse() {
    }


    public MistralAiModelResponse(String object, List<MistralAiModelCard> data) {
        this.object = object;
        this.data = data;
    }
}
