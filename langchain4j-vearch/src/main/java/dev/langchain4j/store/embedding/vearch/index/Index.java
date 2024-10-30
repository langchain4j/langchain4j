package dev.langchain4j.store.embedding.vearch.index;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class Index {

    private String name;
    private IndexType type;
    private IndexParam params;

    public Index() {
    }

    public Index(String name, IndexType type, IndexParam params) {
        setName(name);
        setType(type);
        setParams(params);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IndexType getType() {
        return type;
    }

    public void setType(IndexType type) {
        this.type = type;
    }

    public IndexParam getParams() {
        return params;
    }

    public void setParams(IndexParam params) {
        // do some constraint check
        Class<? extends IndexParam> clazz = type.getCreateSpaceParamClass();
        if (clazz != null && !clazz.isInstance(params)) {
            throw new UnsupportedOperationException(
                String.format("can't assign unknown param of engine %s, please use class %s to assign engine param",
                    type.name(), clazz.getSimpleName()));
        }
        this.params = params;
    }

    public static class Builder {

        private String name;
        private IndexType type;
        private IndexParam params;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(IndexType type) {
            this.type = type;
            return this;
        }

        public Builder params(IndexParam params) {
            this.params = params;
            return this;
        }

        public Index build() {
            return new Index(name, type, params);
        }
    }
}
