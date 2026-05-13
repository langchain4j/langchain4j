package dev.langchain4j.mcp.client;

import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A functional interface that supplies {@code _meta} fields for MCP client
 * requests and notifications based on the given {@link McpCallContext}.
 * Unlike HTTP headers, this applies to all transports.
 */
@FunctionalInterface
public interface McpMetaSupplier extends Function<@Nullable McpCallContext, Map<String, Object>> {}
