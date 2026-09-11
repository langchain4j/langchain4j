package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum McpClientMethod {
    // Legacy protocol only (up to 2025-11-25)
    @JsonProperty("initialize")
    INITIALIZE,
    @JsonProperty("tools/call")
    TOOLS_CALL,
    @JsonProperty("tools/list")
    TOOLS_LIST,
    @JsonProperty("notifications/cancelled")
    NOTIFICATION_CANCELLED,
    // Legacy protocol only (up to 2025-11-25)
    @JsonProperty("notifications/initialized")
    NOTIFICATION_INITIALIZED,
    // Legacy protocol only (up to 2025-11-25)
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
    // Legacy protocol only (up to 2025-11-25)
    @JsonProperty("notifications/roots/list_changed")
    NOTIFICATION_ROOTS_LIST_CHANGED,
    // Legacy protocol only (up to 2025-11-25)
    @JsonProperty("resources/subscribe")
    RESOURCES_SUBSCRIBE,
    // Legacy protocol only (up to 2025-11-25)
    @JsonProperty("resources/unsubscribe")
    RESOURCES_UNSUBSCRIBE,
    @JsonProperty("server/discover")
    SERVER_DISCOVER,
    @JsonProperty("subscriptions/listen")
    SUBSCRIPTIONS_LISTEN
}
