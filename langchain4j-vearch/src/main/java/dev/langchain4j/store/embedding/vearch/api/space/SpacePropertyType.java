package dev.langchain4j.store.embedding.vearch.api.space;

import lombok.Getter;

public enum SpacePropertyType {

    /**
     * keyword is equivalent to string
     */
    KEYWORD("keyword", SpacePropertyParam.KeywordParam.class),
    INTEGER("integer", SpacePropertyParam.IntegerParam.class),
    FLOAT("float", SpacePropertyParam.FloatParam.class),
    VECTOR("vector", SpacePropertyParam.VectorParam.class);

    @Getter
    private final String name;
    @Getter
    private final Class<? extends SpacePropertyParam> paramClass;

    SpacePropertyType(String name, Class<? extends SpacePropertyParam> paramClass) {
        this.name = name;
        this.paramClass = paramClass;
    }
}
