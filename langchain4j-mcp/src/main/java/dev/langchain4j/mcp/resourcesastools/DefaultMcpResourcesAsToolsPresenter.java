package dev.langchain4j.mcp.resourcesastools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.List;

/**
 * Default implementation of {@link McpResourcesAsToolsPresenter}.
 * A presenter that presents MCP resources as tools. It provides two tools: {@code list_resources} and {@code get_resource}.
 *
 * The {@code list_resources} tool lists all available resources. It doesn't take any arguments and returns a JSON array where
 * each object represents a single resource. For example:
 *
 * <pre>
 * [ {
 *   "mcpServer" : "alice",
 *   "uri" : "file:///info",
 *   "uriTemplate" : null,
 *   "name" : "basicInfo",
 *   "description" : "Basic information about Alice",
 *   "mimeType" : "text/plain"
 * }, {
 *   "mcpServer" : "bob",
 *   "uri" : "file:///info",
 *   "uriTemplate" : null,
 *   "name" : "basicInfo",
 *   "description" : "Basic information about Bob",
 *   "mimeType" : "text/plain"
 * } ]
 * </pre>
 *
 * The {@code get_resource} tool retrieves a specific resource based on its MCP server name and URI. It takes two arguments:
 * {@code mcpServer} and {@code uri}. Both arguments are mandatory. For example:
 *
 * The DefaultMcpResourcesAsToolsPresenter provides default names and descriptions of these two tools and their arguments. These
 * descriptions should generally work, but if it's necessary to override them, this can be done in the builder.
 *
 */
public class DefaultMcpResourcesAsToolsPresenter implements McpResourcesAsToolsPresenter {

    private final String nameOfGetResourceTool;
    private final String descriptionOfGetResourceTool;
    private final String descriptionOfMcpServerParameterOfGetResourceTool;
    private final String descriptionOfUriParameterOfGetResourceTool;
    private final String nameOfListResourcesTool;
    private final String descriptionOfListResourcesTool;

    public static final String DEFAULT_NAME_OF_GET_RESOURCE_TOOL = "get_resource";
    public static final String DEFAULT_DESCRIPTION_OF_GET_RESOURCE_TOOL =
            "Retrieves a resource identified by the MCP server name and the resource URI."
                    + "The 'list_resources' tool should be called before this one to obtain the list.";
    public static final String DEFAULT_DESCRIPTION_OF_MCP_SERVER_PARAMETER_OF_GET_RESOURCE_TOOL =
            "The name of the MCP server to get the resource from.";
    public static final String DEFAULT_DESCRIPTION_OF_URI_PARAMETER_OF_GET_RESOURCE_TOOL =
            "The URI of the resource to get.";

    public static final String DEFAULT_NAME_OF_LIST_RESOURCES_TOOL = "list_resources";
    public static final String DEFAULT_DESCRIPTION_OF_LIST_RESOURCES_TOOL = "Lists all available resources.";

    private DefaultMcpResourcesAsToolsPresenter(Builder builder) {
        this(
                Utils.getOrDefault(builder.nameOfGetResourceTool, DEFAULT_NAME_OF_GET_RESOURCE_TOOL),
                Utils.getOrDefault(builder.descriptionOfGetResourceTool, DEFAULT_DESCRIPTION_OF_GET_RESOURCE_TOOL),
                Utils.getOrDefault(
                        builder.descriptionOfMcpServerParameterOfGetResourceTool,
                        DEFAULT_DESCRIPTION_OF_MCP_SERVER_PARAMETER_OF_GET_RESOURCE_TOOL),
                Utils.getOrDefault(
                        builder.descriptionOfUriParameterOfGetResourceTool,
                        DEFAULT_DESCRIPTION_OF_URI_PARAMETER_OF_GET_RESOURCE_TOOL),
                Utils.getOrDefault(builder.nameOfListResourcesTool, DEFAULT_NAME_OF_LIST_RESOURCES_TOOL),
                Utils.getOrDefault(builder.descriptionOfListResourcesTool, DEFAULT_DESCRIPTION_OF_LIST_RESOURCES_TOOL));
    }

    protected DefaultMcpResourcesAsToolsPresenter(
            String nameOfGetResourceTool,
            String descriptionOfGetResourceTool,
            String descriptionOfMcpServerParameterOfGetResourceTool,
            String descriptionOfUriParameterOfGetResourceTool,
            String nameOfListResourcesTool,
            String descriptionOfListResourcesTool) {
        this.nameOfGetResourceTool = nameOfGetResourceTool;
        this.descriptionOfGetResourceTool = descriptionOfGetResourceTool;
        this.descriptionOfMcpServerParameterOfGetResourceTool = descriptionOfMcpServerParameterOfGetResourceTool;
        this.descriptionOfUriParameterOfGetResourceTool = descriptionOfUriParameterOfGetResourceTool;
        this.nameOfListResourcesTool = nameOfListResourcesTool;
        this.descriptionOfListResourcesTool = descriptionOfListResourcesTool;
    }

    @Override
    public ToolSpecification createListResourcesSpecification() {
        return ToolSpecification.builder()
                .name(nameOfListResourcesTool)
                .description(descriptionOfListResourcesTool)
                .parameters(JsonObjectSchema.builder().build())
                .build();
    }

    @Override
    public ToolExecutor createListResourcesExecutor(List<McpClient> mcpClients) {
        return new ListResourcesToolExecutor(mcpClients);
    }

    @Override
    public ToolSpecification createGetResourceSpecification() {
        return ToolSpecification.builder()
                .name(nameOfGetResourceTool)
                .description(descriptionOfGetResourceTool)
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("mcpServer", descriptionOfMcpServerParameterOfGetResourceTool)
                        .addStringProperty("uri", descriptionOfUriParameterOfGetResourceTool)
                        .build())
                .build();
    }

    @Override
    public ToolExecutor createGetResourceExecutor(List<McpClient> mcpClients) {
        return new GetResourceToolExecutor(mcpClients);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String nameOfGetResourceTool;
        private String descriptionOfGetResourceTool;
        private String descriptionOfMcpServerParameterOfGetResourceTool;
        private String descriptionOfUriParameterOfGetResourceTool;
        private String nameOfListResourcesTool;
        private String descriptionOfListResourcesTool;

        /**
         * Overrides the name of the `get_resource` tool.
         */
        public Builder nameOfGetResourceTool(String nameOfGetResourceTool) {
            this.nameOfGetResourceTool = nameOfGetResourceTool;
            return this;
        }

        /**
         * Overrides the description of the `get_resource` tool.
         */
        public Builder descriptionOfGetResourceTool(String descriptionOfGetResourceTool) {
            this.descriptionOfGetResourceTool = descriptionOfGetResourceTool;
            return this;
        }

        /**
         * Overrides the description of the `mcp_server` parameter of the `get_resource` tool.
         */
        public Builder descriptionOfMcpServerParameterOfGetResourceTool(
                String descriptionOfMcpServerParameterOfGetResourceTool) {
            this.descriptionOfMcpServerParameterOfGetResourceTool = descriptionOfMcpServerParameterOfGetResourceTool;
            return this;
        }

        /**
         * Overrides the description of the `uri` parameter of the `get_resource` tool.
         */
        public Builder descriptionOfUriParameterOfGetResourceTool(String descriptionOfUriParameterOfGetResourceTool) {
            this.descriptionOfUriParameterOfGetResourceTool = descriptionOfUriParameterOfGetResourceTool;
            return this;
        }

        /**
         * Overrides the name of the `list_resources` tool.
         */
        public Builder nameOfListResourcesTool(String nameOfListResourcesTool) {
            this.nameOfListResourcesTool = nameOfListResourcesTool;
            return this;
        }

        /**
         * Overrides the description of the `list_resources` tool.
         */
        public Builder descriptionOfListResourcesTool(String descriptionOfListResourcesTool) {
            this.descriptionOfListResourcesTool = descriptionOfListResourcesTool;
            return this;
        }

        public DefaultMcpResourcesAsToolsPresenter build() {
            return new DefaultMcpResourcesAsToolsPresenter(this);
        }
    }
}
