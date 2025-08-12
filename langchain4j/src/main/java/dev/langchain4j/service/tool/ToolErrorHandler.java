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
     * Currently, there are 2 options how tool error can be handled:
     * - return ToolErrorHandlerResult with text that will be sent to the LLM. LLM will have an option to react to it and possibly retry
     * - re-throw an exception (in this case the AI Service invocation fails with this exception)
     *
     * @param error   TODO
     * @param context TODO
     * @return TODO
     */
    ToolErrorHandlerResult handle(Exception error, ToolErrorContext context);
    // TODO error: Throwable or RuntimeException?
}
