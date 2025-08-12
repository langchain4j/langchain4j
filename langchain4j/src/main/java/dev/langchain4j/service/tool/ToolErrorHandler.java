package dev.langchain4j.service.tool;

/**
 * TODO
 *
 * @since 1.4.0
 */
@FunctionalInterface
public interface ToolErrorHandler {

    /**
     * TODO
     * TODO options: return String, re-throw, etc
     *
     * @param error
     * @param context
     * @return
     */
    ToolErrorHandlerResult handle(Throwable error, ToolErrorContext context);
}
