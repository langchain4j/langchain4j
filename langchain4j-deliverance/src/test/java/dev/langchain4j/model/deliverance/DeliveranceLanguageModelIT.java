package dev.langchain4j.model.deliverance;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import io.teknek.deliverance.math.WrappedForkJoinPool;
import io.teknek.deliverance.model.AutoModelForCausaLm;
import io.teknek.deliverance.tensor.operations.ConfigurableTensorProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveranceLanguageModelIT {

    static LanguageModel model;

    @BeforeAll
    static void setup() {
        AutoModelForCausaLm.Builder builder = DeliveranceModels.builder((Path) null,
                DeliveranceTestUtils.GEMMA_MODEL_NAME);
        builder.withTensorProvider(new ConfigurableTensorProvider(builder.getAllocator(),
                new WrappedForkJoinPool(WrappedForkJoinPool.autoSizeByCores())));
        model = DeliveranceLanguageModel.builder()
                .modelBuilder(builder)
                .customizeGeneratorParameters(parameters -> parameters
                        .withTemperature(0.0f)
                        .withTopP(0.9f)
                        .withMaxTokens(64))
                .build();
    }

    @Test
    void should_send_prompt_and_return_response() {
        Response<String> response = model.generate("When is the best time of year to visit Japan?");

        assertThat(response.content()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
