package dev.langchain4j.store.embedding.vearch.field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.index.Index;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.store.embedding.vearch.field.FieldType.NUMERIC_TYPES;

/**
 * Support field type: INTEGER, LONG, FLOAT, DOUBLE
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class NumericField extends Field {

    public NumericField() {
    }

    public NumericField(String name, FieldType fieldType, Index index) {
        super(name, fieldType, index);
        if (!NUMERIC_TYPES.contains(fieldType)) {
            throw new IllegalArgumentException("Cannot use type " + fieldType + ", supported numeric types are: " + NUMERIC_TYPES);
        }
    }

    public static NumericParamBuilder builder() {
        return new NumericParamBuilder();
    }

    public static class NumericParamBuilder extends FieldParamBuilder<NumericField, NumericParamBuilder> {

        private FieldType fieldType;

        public NumericParamBuilder fieldType(FieldType fieldType) {
            this.fieldType = fieldType;
            return this;
        }

        @Override
        protected NumericParamBuilder self() {
            return this;
        }

        @Override
        public NumericField build() {
            return new NumericField(name, fieldType, index);
        }
    }
}
