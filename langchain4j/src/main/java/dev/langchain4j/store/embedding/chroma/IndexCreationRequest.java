package dev.langchain4j.store.embedding.chroma;

public class IndexCreationRequest {

    private String type;
    private String title;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return "IndexCreationRequest{" +
                "type='" + type + '\'' +
                ", title='" + title + '\'' +
                '}';
    }

}
