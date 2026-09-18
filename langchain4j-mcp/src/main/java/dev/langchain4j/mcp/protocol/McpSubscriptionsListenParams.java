package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.List;

@Internal
public class McpSubscriptionsListenParams extends McpClientParams {

    private Notifications notifications;

    public McpSubscriptionsListenParams() {}

    public Notifications getNotifications() {
        return notifications;
    }

    public void setNotifications(Notifications notifications) {
        this.notifications = notifications;
    }

    @Internal
    public static class Notifications {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean toolsListChanged;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean promptsListChanged;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean resourcesListChanged;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<String> resourceSubscriptions;

        public Notifications() {}

        public Boolean getToolsListChanged() {
            return toolsListChanged;
        }

        public void setToolsListChanged(Boolean toolsListChanged) {
            this.toolsListChanged = toolsListChanged;
        }

        public Boolean getPromptsListChanged() {
            return promptsListChanged;
        }

        public void setPromptsListChanged(Boolean promptsListChanged) {
            this.promptsListChanged = promptsListChanged;
        }

        public Boolean getResourcesListChanged() {
            return resourcesListChanged;
        }

        public void setResourcesListChanged(Boolean resourcesListChanged) {
            this.resourcesListChanged = resourcesListChanged;
        }

        public List<String> getResourceSubscriptions() {
            return resourceSubscriptions;
        }

        public void setResourceSubscriptions(List<String> resourceSubscriptions) {
            this.resourceSubscriptions = resourceSubscriptions;
        }
    }
}
