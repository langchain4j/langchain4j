package dev.langchain4j.mcp.client.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ClientMethod {
    @JsonProperty("initialize")
    INITIALIZE,
    @JsonProperty("tools/call")
    TOOLS_CALL,
    @JsonProperty("tools/list")
    TOOLS_LIST,
    @JsonProperty("notifications/cancelled")
    NOTIFICATION_CANCELLED,
    @JsonProperty("notifications/initialized")
    NOTIFICATION_INITIALIZED,
    @JsonProperty("ping")
    PING,
    @JsonProperty("resources/list")
    RESOURCES_LIST,
    @JsonProperty("resources/read")
    RESOURCES_READ,
    @JsonProperty("resources/templates/list")
    RESOURCES_TEMPLATES_LIST,
    @JsonProperty("prompts/list")
    PROMPTS_LIST,
    @JsonProperty("prompts/get")
    PROMPTS_GET
}
