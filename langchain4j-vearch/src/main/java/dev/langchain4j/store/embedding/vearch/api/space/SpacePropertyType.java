package dev.langchain4j.store.embedding.vearch.api.space;

public enum SpacePropertyType {

    /**
     * keyword is equivalent to string
     */
    KEYWORD("keyword"),
    INTEGER("integer"),
    FLOAT("float"),
    VECTOR("vector");

    private String name;

    SpacePropertyType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
