package dev.langchain4j.langfuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TracingToolExecutor {
    private final LangfuseTracer tracer;
    private final ObjectMapper objectMapper;

    public TracingToolExecutor(LangfuseTracer tracer) {
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper();
    }

    public Object executeTool(ToolExecutionRequest request, Object toolInstance) {
        String traceId = UUID.randomUUID().toString();
        String spanName = String.format("tool_execution/%s", request.name());
        String spanId = tracer.startSpan(
                traceId, spanName, Map.of("tool_name", request.name(), "tool_id", request.id()), "START");

        try {
            tracer.startTrace(traceId, Map.of("tool_name", request.name()), null);
            tracer.setTag(traceId, "tool_id", request.id());
            tracer.logEvent(traceId, "tool_input", Map.of("arguments", request.arguments()));

            Object result = executeToolMethod(request, toolInstance);

            tracer.logEvent(traceId, "tool_output", Map.of("result", result));
            tracer.updateSpan(spanId, Map.of("result", result), "SUCCESS");
            return result;

        } catch (Exception e) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error_type", e.getClass().getName());
            errorData.put("error_message", e.getMessage());
            errorData.put("stack_trace", Arrays.toString(e.getStackTrace()));
            tracer.logEvent(traceId, "tool_error", errorData);
            tracer.updateSpan(spanId, errorData, "ERROR");
            throw new RuntimeException("Tool execution failed", e);
        }
    }

    private Object executeToolMethod(ToolExecutionRequest request, Object toolInstance) throws Exception {
        Method toolMethod = findToolMethod(toolInstance.getClass(), request.name());
        if (toolMethod == null) {
            throw new IllegalArgumentException(String.format("No @Tool method found with name '%s'", request.name()));
        }

        toolMethod.setAccessible(true);

        Object[] arguments = parseArguments(request.arguments(), toolMethod);

        return toolMethod.invoke(toolInstance, arguments);
    }

    private Object[] parseArguments(String argumentsJson, Method method) throws Exception {
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        Object[] parsedArgs = new Object[parameters.length];

        if (parameters.length == 1) {
            parsedArgs[0] = objectMapper.readValue(argumentsJson, parameters[0].getType());
        } else {
            var argsMap = objectMapper.readValue(argumentsJson, Map.class);

            for (int i = 0; i < parameters.length; i++) {
                String paramName = parameters[i].getName();
                if (argsMap.containsKey(paramName)) {
                    Object value = argsMap.get(paramName);
                    if (value != null) {
                        parsedArgs[i] = objectMapper.convertValue(value, parameters[i].getType());
                    }
                }
            }
        }

        return parsedArgs;
    }

    private Method findToolMethod(Class<?> toolClass, String toolName) {
        return Arrays.stream(toolClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> {
                    Tool tool = method.getAnnotation(Tool.class);
                    String name = tool.name().isEmpty() ? method.getName() : tool.name();
                    return name.equals(toolName);
                })
                .findFirst()
                .orElse(null);
    }
}
