package dev.langchain4j.store.embedding.vearch.field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.index.Index;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.store.embedding.vearch.field.FieldType.STRING_TYPES;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class StringField extends Field {

    public StringField() {
    }

    public StringField(String name, FieldType fieldType, Index index) {
        super(name, fieldType, index);
        if (!STRING_TYPES.contains(fieldType)) {
            throw new IllegalArgumentException("Cannot use type " + fieldType + ", supported string types are: " + STRING_TYPES);
        }
    }

    public static StringParamBuilder builder() {
        return new StringParamBuilder();
    }

    public static class StringParamBuilder extends FieldParamBuilder<StringField, StringParamBuilder> {

        private FieldType fieldType;

        public StringParamBuilder fieldType(FieldType fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        @Override
        protected StringParamBuilder self() {
            return this;
        }

        @Override
        public StringField build() {
            return new StringField(name, fieldType, index);
        }
    }
}
