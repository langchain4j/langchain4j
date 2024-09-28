package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SpacePropertyType {

    /**
     * keyword is equivalent to string
     */
    @JsonProperty("string")
    STRING(SpacePropertyParam.StringParam.class),
    @JsonProperty("integer")
    INTEGER(SpacePropertyParam.IntegerParam.class),
    @JsonProperty("float")
    FLOAT(SpacePropertyParam.FloatParam.class),
    @JsonProperty("vector")
    VECTOR(SpacePropertyParam.VectorParam.class);

    private final Class<? extends SpacePropertyParam> paramClass;

    SpacePropertyType(Class<? extends SpacePropertyParam> paramClass) {
        this.paramClass = paramClass;
    }

    public Class<? extends SpacePropertyParam> getParamClass() {
        return paramClass;
    }
}
