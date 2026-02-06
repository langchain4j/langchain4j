package dev.langchain4j.service.tool.search;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;

import java.util.List;

// TODO name, location

/**
 * @since 1.12.0
 */
public interface ToolSearchStrategy {

    List<ToolSpecification> toolSearchTools(InvocationContext invocationContext); // TODO name

    ToolSearchResult search(ToolSearchRequest toolSearchRequest); // TODO name
}
