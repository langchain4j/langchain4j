package dev.langchain4j.data.document.splitter.oracle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encapsulate chunks
 *
 * dbms_vector_chain.utl_to_chunks returns chunks in the following format:
 * {"chunk_id": id, "chunk_offset": offset, "chunk_length": length, "chunk_data": "content"}
 */
public class Chunk {

    private int chunk_id;
    private int chunk_offset;
    private int chunk_length;
    private String chunk_data;

    public Chunk() {}

    @JsonProperty("chunk_id")
    public void setId(int id) {
        this.chunk_id = id;
    }

    @JsonProperty("chunk_offset")
    public void setOffset(int offset) {
        this.chunk_offset = offset;
    }

    @JsonProperty("chunk_length")
    public void setLength(int length) {
        this.chunk_length = length;
    }

    @JsonProperty("chunk_data")
    public void setData(String data) {
        this.chunk_data = data;
    }

    @JsonProperty("chunk_id")
    public int getId() {
        return chunk_id;
    }

    @JsonProperty("chunk_offset")
    public int getOffset() {
        return chunk_offset;
    }

    @JsonProperty("chunk_length")
    public int getLength() {
        return chunk_length;
    }

    @JsonProperty("chunk_data")
    public String getData() {
        return chunk_data;
    }
}
