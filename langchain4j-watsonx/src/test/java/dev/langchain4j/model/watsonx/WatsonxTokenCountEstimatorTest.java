package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.tokenization.TokenizationParameters;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse;
import com.ibm.watsonx.ai.tokenization.TokenizationResponse.Result;
import com.ibm.watsonx.ai.tokenization.TokenizationService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WatsonxTokenCountEstimatorTest {

    @Mock
    TokenizationService mockTokenizationService;

    @Test
    void testTokenize() {

        when(mockTokenizationService.tokenize("tokenize", null))
                .thenReturn(new TokenizationResponse("my-model", new Result(10, List.of())));

        TokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                .service(mockTokenizationService)
                .build();

        var response = tokenCountEstimator.estimateTokenCountInText("tokenize");
        assertEquals(10, response);
    }

    @Test
    void testTokenizeWithParameters() {

        var parameters = TokenizationParameters.builder()
                .projectId("project-id")
                .modelId("model-id")
                .returnTokens(true)
                .spaceId("space-id")
                .build();

        when(mockTokenizationService.tokenize("tokenize", parameters))
                .thenReturn(new TokenizationResponse("model-id", new Result(10, List.of())));

        WatsonxTokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                .service(mockTokenizationService)
                .build();

        var response = tokenCountEstimator.estimateTokenCountInText("tokenize", parameters);
        assertEquals(10, response);
    }

    @Test
    void testEstimateTokenCountInMessage() {

        WatsonxTokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                .service(mockTokenizationService)
                .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> tokenCountEstimator.estimateTokenCountInMessage(UserMessage.from("test")));
    }

    @Test
    void testEstimateTokenCountInMessages() {

        WatsonxTokenCountEstimator tokenCountEstimator = WatsonxTokenCountEstimator.builder()
                .service(mockTokenizationService)
                .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> tokenCountEstimator.estimateTokenCountInMessages(List.of(UserMessage.from("test"))));
    }
}
