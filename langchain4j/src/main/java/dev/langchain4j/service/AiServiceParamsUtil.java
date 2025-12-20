package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

class AiServiceParamsUtil {

    private AiServiceParamsUtil() { }

    static ChatRequestParameters chatRequestParameters(
            Method method,
            Object[] args,
            ToolServiceContext toolServiceContext,
            ResponseFormat responseFormat) {
        ChatRequestParameters defaultParams = ChatRequestParameters.builder()
                .toolSpecifications(toolServiceContext.toolSpecifications())
                .responseFormat(responseFormat)
                .build();
        return findArgumentOfType(ChatRequestParameters.class, args, method.getParameters())
                .map(p -> p.defaultedBy(defaultParams))
                .orElse(defaultParams);
    }

    static <P> Optional<P> findArgumentOfType(Class<P> paramType, Object[] args, Parameter[] params) {
        if (args == null) {
            return Optional.empty();
        }
        for (int i = 0; i < params.length; i++) {
            Parameter parameter = params[i];
            if (paramType.isAssignableFrom(parameter.getType())) {
                P param = (P) args[i];
                ensureNotNull(param, paramType.getSimpleName());
                return Optional.of(param);
            }
        }
        return Optional.empty();
    }

    static ChatRequestParameters chatRequestParameters(
            List<? extends Object> args,
            List<ToolSpecification> toolSpecifications) {
        ChatRequestParameters defaultParams = ChatRequestParameters.builder()
                .toolSpecifications(toolSpecifications)
                .build();
        return findArgumentOfType(ChatRequestParameters.class, args)
                .map(p -> p.defaultedBy(defaultParams))
                .orElse(defaultParams);
    }

    static <P> Optional<P> findArgumentOfType(Class<P> paramType, List<? extends Object> args) {
        if (args == null) {
            return Optional.empty();
        }
        for (Object arg : args) {
            if (arg != null && paramType.isAssignableFrom(arg.getClass())) {
                P param = (P) arg;
                ensureNotNull(param, paramType.getSimpleName());
                return Optional.of(param);
            }
        }
        return Optional.empty();
    }
}
