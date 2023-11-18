package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /**
     * The temperature of the model. Increasing the temperature will make the model answer more creatively. (Default: 0.8)
     */
    private Double temperature;
}
