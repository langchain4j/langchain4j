package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * request options in completion/embedding API
 *
 * @see <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama REST API Doc</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Options {

    private Double temperature;
    private Integer topK;
    private Double topP;
    private Double repeatPenalty;
    private Integer seed;
    private Integer numPredict;
    private List<String> stop;
}
