package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;

@Internal
class Database {

    private String name;

    public Database() {}

    public Database(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
