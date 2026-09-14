package dev.langchain4j.service;

import static java.lang.reflect.Modifier.isStatic;

import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.tool.ToolService;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs a one-time notice when an AI Service is built with tools but without explicitly configured
 * tool error handlers, so that the default behavior is a deliberate choice rather than an accident.
 * <p>
 * Nothing is logged for AI Services whose methods are all asynchronous or reactive: those modes
 * already behave the way the defaults are expected to behave in the future.
 * <p>
 * The fully qualified name of this class is part of the public contract: the notice tells users to silence
 * it by setting the log level of this logger, so applications put it in their logging configuration.
 * Renaming or moving this class breaks that configuration silently.
 */
class ToolErrorHandlingNotice {

    private static final Logger log = LoggerFactory.getLogger(ToolErrorHandlingNotice.class);


    /**
     * Package-private so that tests can log the notice more than once per JVM.
     */
    static final AtomicBoolean ALREADY_LOGGED = new AtomicBoolean();

    private ToolErrorHandlingNotice() {}

    static void logOnceIfNeeded(AiServiceContext context, Predicate<Type> asynchronousOrReactive) {
        List<Default> unconfirmedDefaults = unconfirmedDefaults(context, asynchronousOrReactive);
        if (unconfirmedDefaults.isEmpty()) {
            return;
        }
        if (!ALREADY_LOGGED.compareAndSet(false, true)) {
            return;
        }
        log.warn(message(context.aiServiceClass, unconfirmedDefaults));
    }

    /**
     * The defaults this AI Service relies on without having chosen them explicitly.
     */
    static List<Default> unconfirmedDefaults(AiServiceContext context, Predicate<Type> asynchronousOrReactive) {
        ToolService toolService = context.toolService;
        boolean hasTools =
                !toolService.toolSpecifications().isEmpty() || !toolService.toolProviders().isEmpty();
        if (!hasTools || !hasBlockingMethod(context.aiServiceClass, asynchronousOrReactive)) {
            return List.of();
        }

        List<Default> defaults = new ArrayList<>();
        if (!toolService.hasExplicitArgumentsErrorHandler()) {
            defaults.add(Default.TOOL_ARGUMENTS_ERROR);
        }
        if (!toolService.hasExplicitExecutionErrorHandler()) {
            defaults.add(Default.TOOL_EXECUTION_ERROR);
        }
        return defaults;
    }

    private static boolean hasBlockingMethod(Class<?> aiServiceClass, Predicate<Type> asynchronousOrReactive) {
        for (Method method : aiServiceClass.getMethods()) {
            if (isStatic(method.getModifiers())
                    || method.isDefault()
                    || method.getDeclaringClass() == Object.class
                    || method.getDeclaringClass() == ChatMemoryAccess.class) {
                continue;
            }
            if (!asynchronousOrReactive.test(method.getGenericReturnType())) {
                return true;
            }
        }
        return false;
    }


    static String message(Class<?> aiServiceClass, List<Default> unconfirmedDefaults) {
        StringBuilder message = new StringBuilder()
                .append("AI Service '")
                .append(aiServiceClass.getName())
                .append("' is configured with tools, but ")
                .append(unconfirmedDefaults.size() == 1 ? "a tool error handler is" : "tool error handlers are")
                .append(" not configured explicitly. The current default behavior is:");

        for (Default unconfirmedDefault : unconfirmedDefaults) {
            message.append("\n  - ").append(unconfirmedDefault.description);
        }

        if (unconfirmedDefaults.contains(Default.TOOL_EXECUTION_ERROR)) {
            message.append("\nUsually, the message of an exception is written for developers, not for the LLM: "
                    + "it can expose "
                    + "internal application details (file paths, SQL, credentials embedded in error messages, "
                    + "responses of downstream services) to the LLM provider, to the chat memory "
                    + "and to whoever can see the output of the AI Service.");
        }

        message.append("\nThese defaults are planned to change in an upcoming release. "
                + "To avoid a breaking change when that happens, "
                + "we recommend specifying error handlers explicitly.");

        message.append("\nRecommended (this is also the behavior the defaults are planned to change to):");
        for (Default unconfirmedDefault : unconfirmedDefaults) {
            message.append("\n  ").append(unconfirmedDefault.recommendedSnippet);
        }

        if (unconfirmedDefaults.contains(Default.TOOL_EXECUTION_ERROR)) {
            message.append("\nWith the recommended setting, a tool tells the LLM about a failure by throwing an "
                    + "exception that implements dev.langchain4j.exception.ToolErrorVisibleToLlm, for example:"
                    + "\n  throw ToolErrorVisibleToLlm.of(\"There is no order with this ID.\");");
        }

        message.append("\nTo keep the current behavior:");
        for (Default unconfirmedDefault : unconfirmedDefaults) {
            message.append("\n  ").append(unconfirmedDefault.currentSnippet);
        }

        return message.append("\nOther options are described in ")
                .append("https://docs.langchain4j.dev/tutorials/tools#error-handling")
                .append("\nThis message is logged once per JVM. To turn it off, set the log level of the '")
                .append(ToolErrorHandlingNotice.class.getName())
                .append("' logger to OFF.")
                .toString();
    }

    /**
     * A tool error handling default that the AI Service relies on implicitly.
     */
    enum Default {
        TOOL_ARGUMENTS_ERROR(
                "an error in the arguments generated by the LLM fails the AI Service invocation",
                ".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())"
                        + " // the LLM sees what went wrong and can retry with corrected arguments",
                ".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation())"),
        TOOL_EXECUTION_ERROR(
                "an exception thrown by a tool is sent to the LLM as the message of that exception",
                ".toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())"
                        + " // only exceptions written for the LLM reach it, everything else fails the invocation",
                ".toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm())");

        private final String description;
        private final String recommendedSnippet;
        private final String currentSnippet;

        Default(String description, String recommendedSnippet, String currentSnippet) {
            this.description = description;
            this.recommendedSnippet = recommendedSnippet;
            this.currentSnippet = currentSnippet;
        }
    }
}
