package dev.langchain4j.store.embedding.vearch.field;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public enum FieldType {

    /**
     * keyword is equivalent to string
     */
    @JsonProperty("string")
    STRING(StringField.class),
    @JsonProperty("stringArray")
    STRING_ARRAY(StringField.class),
    @JsonProperty("integer")
    INTEGER(NumericField.class),
    @JsonProperty("long")
    LONG(NumericField.class),
    @JsonProperty("float")
    FLOAT(NumericField.class),
    @JsonProperty("double")
    DOUBLE(NumericField.class),
    @JsonProperty("vector")
    VECTOR(VectorField.class);

    static final Set<FieldType> NUMERIC_TYPES = Set.of(INTEGER, LONG, FLOAT, DOUBLE);
    static final Set<FieldType> STRING_TYPES = Set.of(STRING, STRING_ARRAY);

    private final Class<? extends Field> paramClass;

    FieldType(Class<? extends Field> paramClass) {
        this.paramClass = paramClass;
    }

    public Class<? extends Field> getParamClass() {
        return paramClass;
    }
}
