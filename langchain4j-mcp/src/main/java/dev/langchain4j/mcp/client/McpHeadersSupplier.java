package dev.langchain4j.mcp.client;

import java.util.Map;
import java.util.function.Function;

/**
 * A functional interface that supplies HTTP headers for MCP client requests
 * based on the given {@link McpCallContext}.
 */
@FunctionalInterface
public interface McpHeadersSupplier extends Function<McpCallContext, Map<String, String>> {}
