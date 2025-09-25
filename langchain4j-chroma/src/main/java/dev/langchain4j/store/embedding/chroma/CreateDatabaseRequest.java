package dev.langchain4j.store.embedding.chroma;

class CreateDatabaseRequest {

    private final String name;

    CreateDatabaseRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
