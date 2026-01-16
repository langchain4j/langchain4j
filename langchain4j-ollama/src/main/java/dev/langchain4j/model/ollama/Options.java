package dev.langchain4j.model.ollama;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * request options in completion/embedding API
 *
 * @see <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama REST API Doc</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class Options {

    private Integer mirostat;
    private Double mirostatEta;
    private Double mirostatTau;
    private Integer repeatLastN;
    private Double temperature;
    private Integer topK;
    private Double topP;
    private Double repeatPenalty;
    private Integer seed;
    private Integer numPredict;
    private Integer numCtx;
    private List<String> stop;
    private Double minP;

    Options() {}

    Options(Builder builder) {
        this.mirostat = builder.mirostat;
        this.mirostatEta = builder.mirostatEta;
        this.mirostatTau = builder.mirostatTau;
        this.repeatLastN = builder.repeatLastN;
        this.temperature = builder.temperature;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.repeatPenalty = builder.repeatPenalty;
        this.seed = builder.seed;
        this.numPredict = builder.numPredict;
        this.numCtx = builder.numCtx;
        this.stop = builder.stop;
        this.minP = builder.minP;
    }

    public Integer getMirostat() {
        return mirostat;
    }

    public void setMirostat(Integer mirostat) {
        this.mirostat = mirostat;
    }

    public Double getMirostatEta() {
        return mirostatEta;
    }

    public void setMirostatEta(Double mirostatEta) {
        this.mirostatEta = mirostatEta;
    }

    public Double getMirostatTau() {
        return mirostatTau;
    }

    public void setMirostatTau(Double mirostatTau) {
        this.mirostatTau = mirostatTau;
    }

    public Integer getRepeatLastN() {
        return repeatLastN;
    }

    public void setRepeatLastN(Integer repeatLastN) {
        this.repeatLastN = repeatLastN;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Double getRepeatPenalty() {
        return repeatPenalty;
    }

    public void setRepeatPenalty(Double repeatPenalty) {
        this.repeatPenalty = repeatPenalty;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public Integer getNumPredict() {
        return numPredict;
    }

    public void setNumPredict(Integer numPredict) {
        this.numPredict = numPredict;
    }

    public Integer getNumCtx() {
        return numCtx;
    }

    public void setNumCtx(Integer numCtx) {
        this.numCtx = numCtx;
    }

    public List<String> getStop() {
        return stop;
    }

    public void setStop(List<String> stop) {
        this.stop = stop;
    }

    public Double getMinP() {
        return minP;
    }

    public void setMinP(Double minP) {
        this.minP = minP;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private Integer mirostat;
        private Double mirostatEta;
        private Double mirostatTau;
        private Integer repeatLastN;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double repeatPenalty;
        private Integer seed;
        private Integer numPredict;
        private Integer numCtx;
        private List<String> stop;
        private Double minP;

        Builder mirostat(Integer mirostat) {
            this.mirostat = mirostat;
            return this;
        }

        Builder mirostatEta(Double mirostatEta) {
            this.mirostatEta = mirostatEta;
            return this;
        }

        Builder mirostatTau(Double mirostatTau) {
            this.mirostatTau = mirostatTau;
            return this;
        }

        Builder repeatLastN(Integer repeatLastN) {
            this.repeatLastN = repeatLastN;
            return this;
        }

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

        Builder minP(Double minP) {
            this.minP = minP;
            return this;
        }

        Options build() {
            return new Options(this);
        }
    }
}
