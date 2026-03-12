package dev.langchain4j.model.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents the cost structure for a model. Costs are typically per million
 * tokens.
 */
public class Cost {
    private Double input;
    private Double output;

    @JsonProperty("output_audio")
    private Double outputAudio;

    @JsonProperty("input_audio")
    private Double inputAudio;

    @JsonProperty("cache_read")
    private Double cacheRead;

    @JsonProperty("cache_write")
    private Double cacheWrite;

    public Cost() {}

    public Cost(Double input, Double output) {
        this.input = input;
        this.output = output;
    }

    public Double getInput() {
        return input;
    }

    public void setInput(Double input) {
        this.input = input;
    }

    public Double getOutput() {
        return output;
    }

    public void setOutput(Double output) {
        this.output = output;
    }

    public Double getOutputAudio() {
        return outputAudio;
    }

    public void setOutputAudio(Double outputAudio) {
        this.outputAudio = outputAudio;
    }

    public Double getCacheRead() {
        return cacheRead;
    }

    public void setCacheRead(Double cacheRead) {
        this.cacheRead = cacheRead;
    }

    public Double getCacheWrite() {
        return cacheWrite;
    }

    public void setCacheWrite(Double cacheWrite) {
        this.cacheWrite = cacheWrite;
    }

    /**
     * Calculate total cost for a given number of input and output tokens.
     *
     * @param inputTokens  number of input tokens
     * @param outputTokens number of output tokens
     * @return total cost
     */
    public double calculateCost(long inputTokens, long outputTokens) {
        double totalCost = 0.0;
        if (input != null) {
            totalCost += (inputTokens / 1_000_000.0) * input;
        }
        if (output != null) {
            totalCost += (outputTokens / 1_000_000.0) * output;
        }
        return totalCost;
    }

    /**
     * Calculate cost including cache usage.
     *
     * @param inputTokens      number of input tokens
     * @param outputTokens     number of output tokens
     * @param cacheReadTokens  number of cache read tokens
     * @param cacheWriteTokens number of cache write tokens
     * @return total cost
     */
    public double calculateCostWithCache(
            long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        double totalCost = calculateCost(inputTokens, outputTokens);

        if (cacheRead != null) {
            totalCost += (cacheReadTokens / 1_000_000.0) * cacheRead;
        }
        if (cacheWrite != null) {
            totalCost += (cacheWriteTokens / 1_000_000.0) * cacheWrite;
        }

        return totalCost;
    }

    public boolean isFree() {
        return (input == null || input == 0.0) && (output == null || output == 0.0);
    }

    public Double getInputAudio() {
        return inputAudio;
    }

    public void setInputAudio(Double inputAudio) {
        this.inputAudio = inputAudio;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheRead, cacheWrite, input, inputAudio, output, outputAudio);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Cost other = (Cost) obj;
        return Objects.equals(cacheRead, other.cacheRead)
                && Objects.equals(cacheWrite, other.cacheWrite)
                && Objects.equals(input, other.input)
                && Objects.equals(inputAudio, other.inputAudio)
                && Objects.equals(output, other.output)
                && Objects.equals(outputAudio, other.outputAudio);
    }

    @Override
    public String toString() {
        return "Cost [input=" + input + ", output=" + output + ", outputAudio=" + outputAudio + ", inputAudio="
                + inputAudio + ", cacheRead=" + cacheRead + ", cacheWrite=" + cacheWrite + "]";
    }
}
