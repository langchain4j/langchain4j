package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.internal.InternalAgent;

public interface McpClientInstance extends InternalAgent {

    String[] inputKeys();

    String toolName();

    String toolDescription();
}
