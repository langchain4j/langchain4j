package dev.langchain4j.store.embedding.chroma;

class CreateTenantRequest {

    private final String name;

    CreateTenantRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
