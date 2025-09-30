package dev.langchain4j.store.embedding.chroma.model;

public class Tenant {

    private String name;

    public Tenant() {}

    public Tenant(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
