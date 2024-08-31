package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * request options in completion/embedding API
 *
 * @see <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama REST API Doc</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class Options {

    private Double temperature;
    private Integer topK;
    private Double topP;
    private Double repeatPenalty;
    private Integer seed;
    private Integer numPredict;
    private Integer numCtx;
    private List<String> stop;

    Options() {
    }

    Options(Double temperature, Integer topK, Double topP, Double repeatPenalty, Integer seed, Integer numPredict, Integer numCtx, List<String> stop) {
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.repeatPenalty = repeatPenalty;
        this.seed = seed;
        this.numPredict = numPredict;
        this.numCtx = numCtx;
        this.stop = stop;
    }

    static Builder builder() {
        return new Builder();
    }

    Double getTemperature() {
        return temperature;
    }

    void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    Integer getTopK() {
        return topK;
    }

    void setTopK(Integer topK) {
        this.topK = topK;
    }

    Double getTopP() {
        return topP;
    }

    void setTopP(Double topP) {
        this.topP = topP;
    }

    Integer getSeed() {
        return seed;
    }

    void setSeed(Integer seed) {
        this.seed = seed;
    }

    Double getRepeatPenalty() {
        return repeatPenalty;
    }

    void setRepeatPenalty(Double repeatPenalty) {
        this.repeatPenalty = repeatPenalty;
    }

    Integer getNumPredict() {
        return numPredict;
    }

    void setNumPredict(Integer numPredict) {
        this.numPredict = numPredict;
    }

    Integer getNumCtx() {
        return numCtx;
    }

    void setNumCtx(Integer numCtx) {
        this.numCtx = numCtx;
    }

    List<String> getStop() {
        return stop;
    }

    void setStop(List<String> stop) {
        this.stop = stop;
    }

    static class Builder {

        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double repeatPenalty;
        private Integer seed;
        private Integer numPredict;
        private Integer numCtx;
        private List<String> stop;

        Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        Builder repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        Builder numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return this;
        }

        Builder numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return this;
        }

        Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        Options build() {
            return new Options(temperature, topK, topP, repeatPenalty, seed, numPredict, numCtx, stop);
        }
    }
}
