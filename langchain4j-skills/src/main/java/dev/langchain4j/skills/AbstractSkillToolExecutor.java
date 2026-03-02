package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Map;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;

abstract class AbstractSkillToolExecutor implements ToolExecutor {

    protected final boolean throwToolArgumentsExceptions;

    protected AbstractSkillToolExecutor(boolean throwToolArgumentsExceptions) {
        this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
    }

    protected Map<String, Object> parseArguments(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            throwException(message, e);
            return null; // unreachable
        }
    }

    protected String getRequiredArgument(String argumentName, Map<String, Object> arguments) {
        if (isNullOrEmpty(arguments) || !arguments.containsKey(argumentName)) {
            throwException("Missing required tool argument '%s'".formatted(argumentName));
        }
        return arguments.get(argumentName).toString();
    }

    protected void throwException(String message) {
        throwException(message, null);
    }

    protected void throwException(String message, Exception e) {
        if (throwToolArgumentsExceptions) {
            throw e == null
                    ? new ToolArgumentsException(message)
                    : new ToolArgumentsException(message, e);
        } else {
            throw e == null
                    ? new ToolExecutionException(message)
                    : new ToolExecutionException(message, e);
        }
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        throw new IllegalStateException("executeWithContext must be called instead");
    }
}
