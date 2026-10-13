package dev.langchain4j.code.graalvm;

/**
 * Formats the outcome of a code execution into a single string,
 * combining what the code printed with what it evaluated to.
 */
class ExecutionResultFormatter {

    private static final String OUTPUT_PREFIX = "Output:\n";
    private static final String RESULT_PREFIX = "\nResult:\n";

    private ExecutionResultFormatter() {}

    /**
     * @param output what the code printed to stdout/stderr, empty if it printed nothing.
     * @param value  what the code evaluated to, {@code null} if it evaluated to nothing.
     * @return the formatted result.
     */
    static String format(String output, String value) {
        if (output.isEmpty()) {
            return value == null ? "" : value;
        }
        if (value == null) {
            return OUTPUT_PREFIX + output;
        }
        return OUTPUT_PREFIX + output + RESULT_PREFIX + value;
    }
}
