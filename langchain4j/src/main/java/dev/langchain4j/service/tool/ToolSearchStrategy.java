package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

// TODO name, location
public interface ToolSearchStrategy {

    // TODO accept request, return result
    List<ToolSpecification> toolSearchTools(); // TODO accept invocation context, etc

    ToolSearchResult search(ToolSearchRequest request);
}
