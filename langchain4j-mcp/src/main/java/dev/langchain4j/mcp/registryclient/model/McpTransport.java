package dev.langchain4j.mcp.registryclient.model;

import java.util.List;

public class McpTransport {

    private List<McpHeader> headers;
    private String type;
    private String url;

    public List<McpHeader> getHeaders() {
        return headers;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "McpTransport{" +
                "headers=" + headers +
                ", type='" + type + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
