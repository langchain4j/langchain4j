package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.internal.InternalAgent;
import java.util.Map;

public interface McpClientInstance extends InternalAgent {

    /**
     * The input keys of the agent: the configured ones or, for an untyped agent without explicit input keys, the
     * properties of the input schema of its tool, which is resolved at this point if not yet done.
     */
    String[] inputKeys();

    /** Descriptions published by the MCP tool for each input key; resolves the tool if not yet done. */
    default Map<String, String> inputDescriptions() {
        return Map.of();
    }

    /**
     * The name of the MCP tool bound by the agent: the configured one, else the name of the single tool of the MCP
     * server, which is resolved at this point if not yet done.
     */
    String toolName();

    /** The description of the MCP tool; resolves the tool if not yet done. */
    String toolDescription();

    /**
     * Returns the specification of the MCP tool bound by the agent, creating the MCP client and listing the tools of
     * the server if not yet done. The tool is normally resolved on the first invocation of the agent, so this call
     * blocks until the resolution completes and can be used to warm up the agent, or to fail fast, before its first
     * invocation. Inside a declarative agentic system the MCP agent is wrapped: reach it through
     * {@code subagent.as(McpClientInstance.class)} on the corresponding subagent of the
     * {@link dev.langchain4j.agentic.planner.AgentInstance} of the system.
     *
     * @throws McpToolResolutionException if the client cannot be created or the tools cannot be listed
     * @throws dev.langchain4j.agentic.planner.AgenticSystemConfigurationException if the tool is not found
     * @since 1.24.0
     */
    ToolSpecification toolSpecification();
}
