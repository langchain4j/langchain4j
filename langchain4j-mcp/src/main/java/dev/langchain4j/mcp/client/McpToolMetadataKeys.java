package dev.langchain4j.mcp.client;

/**
 * Constants for MCP tool annotation keys, as defined in the MCP specification.
 * These constants are used as keys in the metadata map inside {@link dev.langchain4j.agent.tool.ToolSpecification}.
 */
public class McpToolMetadataKeys {

    /**
     * A human-readable title for the tool as retrieved from the tool annotations,
     * as opposed to the title that is stored in the Tool definition directly.
     * See <a href="https://github.com/modelcontextprotocol/modelcontextprotocol/blob/2025-06-18/schema/2025-06-18/schema.json#L2457">schema.json</a>
     * Value type: String
     */
    public static final String TITLE_ANNOTATION = "title-annotation";

    /**
     * A human-readable title for the tool retrieved from the Tool definition directly,
     * as opposed to the title that is stored in the annotations.
     * See <a href="https://github.com/modelcontextprotocol/modelcontextprotocol/blob/2025-06-18/schema/2025-06-18/schema.json#L67">schema.json</a>
     * Value type: String
     */
    public static final String TITLE = "title";

    /**
     * Indicates whether the tool is read-only.
     * Value type: boolean
     */
    public static final String READ_ONLY_HINT = "readOnlyHint";

    /**
     * Indicates whether the tool is destructive.
     * Value type: boolean
     */
    public static final String DESTRUCTIVE_HINT = "destructiveHint";

    /**
     * Indicates whether the tool is idempotent.
     * Value type: boolean
     */
    public static final String IDEMPOTENT_HINT = "idempotentHint";

    /**
     * Indicates whether the tool may interact with the open world (like internet resources).
     * Value type: boolean
     */
    public static final String OPEN_WORLD_HINT = "openWorldHint";

}
