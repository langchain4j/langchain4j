package dev.langchain4j.model.info;

import java.util.Objects;

/**
 * Represents the token limits for a model.
 */
public class Limit {

    private Integer context;
    private Integer input;
    private Integer output;

    public Limit() {}

    public Limit(Integer context, Integer input, Integer output) {
        this.context = context;
        this.input = input;
        this.output = output;
    }

    public Integer getContext() {
        return context;
    }

    public void setContext(Integer context) {
        this.context = context;
    }

    public Integer getInput() {
        return input;
    }

    public void setInput(Integer input) {
        this.input = input;
    }

    public Integer getOutput() {
        return output;
    }

    public void setOutput(Integer output) {
        this.output = output;
    }

    public boolean canHandle(int requiredContext, int requiredInput, int requiredOutput) {
        boolean contextOk = context == null || context >= requiredContext;
        boolean inputOk = input == null || input >= requiredInput;
        boolean outputOk = output == null || output >= requiredOutput;

        return contextOk && inputOk && outputOk;
    }

    public boolean hasLargeContext() {
        return context != null && context >= 100_000;
    }

    public boolean hasExtendedContext() {
        return context != null && context >= 200_000;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Limit limit = (Limit) o;
        return Objects.equals(context, limit.context)
                && Objects.equals(input, limit.input)
                && Objects.equals(output, limit.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, input, output);
    }

    @Override
    public String toString() {
        return "Limit{" + "context=" + context + ", input=" + input + ", output=" + output + '}';
    }
}
