package dev.langchain4j.service.tool.search;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;

import java.util.List;

// TODO name, location

/**
 * TODO
 *
 * @since 1.12.0
 */
@Experimental
public interface ToolSearchStrategy {

    /**
     * Returns one or more tool specifications that are exposed to the LLM and are used to perform tool searches.
     *
     * @param context the AI Service invocation context
     * @return list containing one or more tool search tools
     */
    List<ToolSpecification> toolSearchTools(InvocationContext invocationContext); // TODO name

    /**
     * Executes a tool search request provided by the LLM and matching it against available tools.
     *
     * @param request the tool search request
     * @return the search result containing matching tool names
     */
    ToolSearchResult search(ToolSearchRequest toolSearchRequest); // TODO name
}
