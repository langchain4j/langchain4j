package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class McpRegistryPong {

    // The accessor below is pong(), not isPong(), so the field says which property it is.
    @JsonProperty("pong")
    private boolean pong;

    public boolean pong() {
        return pong;
    }

    @Override
    public String toString() {
        return "McpRegistryPong{" +
                "pong=" + pong +
                '}';
    }
}
