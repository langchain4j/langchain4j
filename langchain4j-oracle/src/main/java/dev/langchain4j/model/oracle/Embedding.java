package dev.langchain4j.model.oracle;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Embedding {

    private int embed_id;
    private String embed_data;
    private String embed_vector;

    public Embedding() {}

    @JsonProperty("embed_id")
    public void setId(int id) {
        this.embed_id = id;
    }

    @JsonProperty("embed_data")
    public void setData(String data) {
        this.embed_data = data;
    }

    @JsonProperty("embed_vector")
    public void setVector(String vector) {
        this.embed_vector = vector;
    }

    @JsonProperty("embed_id")
    public int getId() {
        return embed_id;
    }

    @JsonProperty("embed_data")
    public String getData() {
        return embed_data;
    }

    @JsonProperty("embed_vector")
    public String getVector() {
        return embed_vector;
    }
}
