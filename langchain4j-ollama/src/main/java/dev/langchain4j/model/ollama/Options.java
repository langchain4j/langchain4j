package dev.langchain4j.model.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
