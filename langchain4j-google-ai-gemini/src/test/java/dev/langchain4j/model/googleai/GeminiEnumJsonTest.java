package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome;
import org.junit.jupiter.api.Test;

class GeminiEnumJsonTest {

    @Test
    void should_read_code_execution_outcome() {
        String json = """
                {
                  "candidates": [{
                    "content": {
                      "role": "model",
                      "parts": [
                        {"executableCode": {"language": "PYTHON", "code": "print(1)"}},
                        {"codeExecutionResult": {"outcome": "OUTCOME_OK", "output": "1"}}
                      ]
                    }
                  }]
                }
                """;

        GeminiGenerateContentResponse response = Json.fromJson(json, GeminiGenerateContentResponse.class);

        GeminiContent.GeminiPart part =
                response.candidates().get(0).content().parts().get(1);
        assertThat(part.codeExecutionResult().outcome()).isEqualTo(GeminiOutcome.OUTCOME_OK);
    }
}
