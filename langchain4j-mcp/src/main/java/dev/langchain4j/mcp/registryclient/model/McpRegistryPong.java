package dev.langchain4j.mcp.registryclient.model;

public class McpRegistryPong {

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
