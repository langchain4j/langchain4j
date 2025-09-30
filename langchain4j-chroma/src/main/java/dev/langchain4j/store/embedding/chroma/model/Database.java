package dev.langchain4j.store.embedding.chroma.model;

public class Database {

    private String name;

    public Database() {}

    public Database(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
