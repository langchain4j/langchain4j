package dev.langchain4j.model.deliverance;

import io.teknek.deliverance.generator.GeneratorParameters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveranceModelSupportTest {

    @Test
    void should_map_supported_chat_request_parameters() {
        DeliveranceChatRequestParameters parameters = DeliveranceChatRequestParameters.builder()
                .temperature(0.1)
                .topP(0.8)
                .topK(12)
                .maxOutputTokens(42)
                .stopSequences("END")
                .ntokens(11)
                .seed(13)
                .guidedChoice("a", "b")
                .logProbs(true)
                .topLogProbs(3)
                .xtcThreshold(0.2)
                .xtcProbability(0.4)
                .includeStopStrInOutput(true)
                .build();

        GeneratorParameters generatorParameters = DeliveranceModelSupport.toGeneratorParameters(parameters);

        assertThat(generatorParameters.temperature).hasValue(0.1f);
        assertThat(generatorParameters.topP).hasValue(0.8f);
        assertThat(generatorParameters.topK).hasValue(12.0f);
        assertThat(generatorParameters.maxTokens).hasValue(42);
        assertThat(generatorParameters.ntokens).hasValue(11);
        assertThat(generatorParameters.seed).hasValue(13);
        assertThat(generatorParameters.stopWords).hasValue(List.of("END"));
        assertThat(generatorParameters.guidedChoice).hasValue(List.of("a", "b"));
        assertThat(generatorParameters.logProbs).hasValue(true);
        assertThat(generatorParameters.topLogProbs).hasValue(3);
        assertThat(generatorParameters.xtcThreshold).hasValue(0.2f);
        assertThat(generatorParameters.xtcProbability).hasValue(0.4f);
        assertThat(generatorParameters.includeStopStrInOutput).hasValue(true);
    }

    @Test
    void should_copy_generator_parameters() {
        GeneratorParameters original = new GeneratorParameters()
                .withTemperature(0.2f)
                .withTopP(0.9f)
                .withTopK(14f)
                .withMaxTokens(64)
                .withNtokens(32)
                .withSeed(7)
                .withStopWords(List.of("STOP"))
                .withGuidedChoice(List.of("x", "y"))
                .withLogProbs(true)
                .withTopLogProbs(5)
                .withXtcThreshold(0.3f)
                .withXtcProbability(0.6f)
                .withIncludeStopStrInOutput(true);

        GeneratorParameters copy = DeliveranceModelSupport.copy(original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.temperature).hasValue(0.2f);
        assertThat(copy.topP).hasValue(0.9f);
        assertThat(copy.topK).hasValue(14f);
        assertThat(copy.maxTokens).hasValue(64);
        assertThat(copy.ntokens).hasValue(32);
        assertThat(copy.seed).hasValue(7);
        assertThat(copy.stopWords).hasValue(List.of("STOP"));
        assertThat(copy.guidedChoice).hasValue(List.of("x", "y"));
        assertThat(copy.logProbs).hasValue(true);
        assertThat(copy.topLogProbs).hasValue(5);
        assertThat(copy.xtcThreshold).hasValue(0.3f);
        assertThat(copy.xtcProbability).hasValue(0.6f);
        assertThat(copy.includeStopStrInOutput).hasValue(true);
    }
}
