package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class SpaceEngine {

    private String name;
    private Long indexSize;
    private RetrievalType retrievalType;
    private RetrievalParam retrievalParam;

    public SpaceEngine() {

    }

    public SpaceEngine(String name, Long indexSize, RetrievalType retrievalType, RetrievalParam retrievalParam) {
        setName(name);
        setIndexSize(indexSize);
        setRetrievalType(retrievalType);
        setRetrievalParam(retrievalParam);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void setRetrievalParam(RetrievalParam retrievalParam) {
        // do some constraint check
        Class<? extends RetrievalParam> clazz = retrievalType.getParamClass();
        if (!clazz.isInstance(retrievalParam)) {
            throw new UnsupportedOperationException(
                    String.format("can't assign unknown param of engine %s, please use class %s to assign engine param",
                            retrievalType.name(), clazz.getSimpleName()));
        }
        this.retrievalParam = retrievalParam;
    }

    public static class Builder {

        private String name;
        private Long indexSize;
        private RetrievalType retrievalType;
        private RetrievalParam retrievalParam;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder indexSize(Long indexSize) {
            this.indexSize = indexSize;
            return this;
        }

        public Builder retrievalType(RetrievalType retrievalType) {
            this.retrievalType = retrievalType;
            return this;
        }

        public Builder retrievalParam(RetrievalParam retrievalParam) {
            this.retrievalParam = retrievalParam;
            return this;
        }

        public SpaceEngine build() {
            return new SpaceEngine(name, indexSize, retrievalType, retrievalParam);
        }
    }
}
