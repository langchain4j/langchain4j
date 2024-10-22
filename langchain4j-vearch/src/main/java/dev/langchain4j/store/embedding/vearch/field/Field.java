package dev.langchain4j.store.embedding.vearch.field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.index.Index;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * As a constraint type of all Space property only
 **/
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public abstract class Field {

    protected String name;
    protected FieldType type;
    protected Index index;

    protected Field() {
    }

    protected Field(String name, FieldType type, Index index) {
        this.name = ensureNotNull(name, "name");
        this.type = ensureNotNull(type, "type");
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public Index getIndex() {
        return index;
    }

    protected abstract static class FieldParamBuilder<C extends Field, B extends FieldParamBuilder<C, B>> {

        protected String name;
        protected Index index;

        public B name(String name) {
            this.name = name;
            return self();
        }

        public B index(Index index) {
            this.index = index;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }
}
