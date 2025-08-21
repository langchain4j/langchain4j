package dev.langchain4j.service.tool;

/**
 * @since 1.4.0
 */
public class ToolExecutionResult { // TODO name, location

    private final boolean isError;
    private final Object result; // TODO name
    private final String resultText; // TODO name

    public ToolExecutionResult(Builder builder) {
        this.isError = builder.isError; // TODO
        this.result = builder.result; // TODO
        this.resultText = builder.resultText; // TODO
    }

    /**
     * TODO
     */
    public boolean isError() {
        return isError;
    }

    /**
     * Returns the result of the tool execution. It is an original object returned from tool. TODO
     *
     * @see #resultText()
     */
    public Object result() {
        return result;
    }

    /**
     * Returns the result of the tool execution in the text form.
     * It is an original object returned from tool that is serialized into JSON.
     *
     * @see #resultText()
     */
    public String resultText() {
        return resultText;
    }

    // TODO eq, hash, tostr

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean isError;
        private Object result;
        private String resultText;

        public Builder isError(boolean isError) {
            this.isError = isError;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder resultText(String resultText) {
            this.resultText = resultText;
            return this;
        }

        public ToolExecutionResult build() {
            return new ToolExecutionResult(this);
        }
    }
}
