package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum McpClientMethod {
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
    PROMPTS_GET,
    @JsonProperty("notifications/roots/list_changed")
    NOTIFICATION_ROOTS_LIST_CHANGED
}
