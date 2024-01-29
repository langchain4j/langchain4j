package dev.langchain4j.store.embedding.vearch;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

public enum SpacePropertyType {

    /**
     * keyword is equivalent to string
     */
    @SerializedName("string")
    STRING(SpacePropertyParam.StringParam.class),
    @SerializedName("integer")
    INTEGER(SpacePropertyParam.IntegerParam.class),
    @SerializedName("float")
    FLOAT(SpacePropertyParam.FloatParam.class),
    @SerializedName("vector")
    VECTOR(SpacePropertyParam.VectorParam.class);

    @Getter
    private final Class<? extends SpacePropertyParam> paramClass;

    SpacePropertyType(Class<? extends SpacePropertyParam> paramClass) {
        this.paramClass = paramClass;
    }
}
