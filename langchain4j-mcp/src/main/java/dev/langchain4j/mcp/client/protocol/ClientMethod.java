package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ClientMethod {
    @JsonProperty("initialize")
    INITIALIZE,
    @JsonProperty("tools/call")
    TOOLS_CALL,
    @JsonProperty("tools/list")
    TOOLS_LIST
}
