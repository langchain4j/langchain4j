package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;

record SupervisorTools(
        List<Object> objectsWithTools,
        Map<ToolSpecification, ToolExecutor> toolsMap,
        Set<String> immediateReturnToolNames,
        List<ToolProvider> toolProviders,
        Integer maxToolCallingRoundTrips,
        Function<ToolExecutionRequest, ToolExecutionResultMessage> hallucinatedToolNameStrategy,
        boolean executeToolsConcurrently,
        Executor concurrentToolsExecutor,
        ToolArgumentsErrorHandler toolArgumentsErrorHandler,
        ToolExecutionErrorHandler toolExecutionErrorHandler) {

    static final SupervisorTools EMPTY =
            new SupervisorTools(List.of(), Map.of(), Set.of(), List.of(), null, null, false, null, null, null);

    SupervisorTools {
        objectsWithTools = List.copyOf(objectsWithTools);
        toolsMap = Map.copyOf(toolsMap);
        immediateReturnToolNames = Set.copyOf(immediateReturnToolNames);
        toolProviders = List.copyOf(toolProviders);
    }

    boolean isEmpty() {
        return objectsWithTools.isEmpty() && toolsMap.isEmpty() && toolProviders.isEmpty();
    }

    void configure(AiServices<PlannerAgent> aiServices) {
        if (!objectsWithTools.isEmpty()) {
            aiServices.tools(objectsWithTools);
        }
        if (!toolsMap.isEmpty()) {
            if (immediateReturnToolNames.isEmpty()) {
                aiServices.tools(toolsMap);
            } else {
                aiServices.tools(toolsMap, immediateReturnToolNames);
            }
        }
        if (!toolProviders.isEmpty()) {
            aiServices.toolProviders(toolProviders);
        }
        if (maxToolCallingRoundTrips != null) {
            aiServices.maxToolCallingRoundTrips(maxToolCallingRoundTrips);
        }
        if (hallucinatedToolNameStrategy != null) {
            aiServices.hallucinatedToolNameStrategy(hallucinatedToolNameStrategy);
        }
        if (executeToolsConcurrently) {
            if (concurrentToolsExecutor != null) {
                aiServices.executeToolsConcurrently(concurrentToolsExecutor);
            } else {
                aiServices.executeToolsConcurrently();
            }
        }
        if (toolArgumentsErrorHandler != null) {
            aiServices.toolArgumentsErrorHandler(toolArgumentsErrorHandler);
        }
        if (toolExecutionErrorHandler != null) {
            aiServices.toolExecutionErrorHandler(toolExecutionErrorHandler);
        }
    }
}
