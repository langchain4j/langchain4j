package dev.langchain4j.model.info;

import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ModelInfoTest implements WithAssertions {

    @Test
    void getters_setters_and_defaults() {
        ModelInfo model = new ModelInfo();

        // Set fields
        model.setId("gpt-4");
        model.setName("GPT-4");
        model.setFamily("gpt");
        model.setAttachment(true);
        model.setReasoning(true);
        model.setToolCall(true);
        model.setTemperature(true);
        model.setOpenWeights(true);
        model.setStructuredOutput(true);
        model.setReleaseDate("2025-02-01");
        model.setLastUpdated("2025-03-01");
        model.setStatus("preview");

        // Verify getters
        assertThat(model.getId()).isEqualTo("gpt-4");
        assertThat(model.getName()).isEqualTo("GPT-4");
        assertThat(model.getFamily()).isEqualTo("gpt");
        assertThat(model.getAttachment()).isTrue();
        assertThat(model.getReasoning()).isTrue();
        assertThat(model.getToolCall()).isTrue();
        assertThat(model.getTemperature()).isTrue();
        assertThat(model.hasOpenWeights()).isTrue();
        assertThat(model.getStructuredOutput()).isTrue();
        assertThat(model.getReleaseDate()).isEqualTo("2025-02-01");
        assertThat(model.getLastUpdated()).isEqualTo("2025-03-01");
        assertThat(model.getStatus()).isEqualTo("preview");
    }

    @Test
    void utility_methods() {
        ModelInfo model = new ModelInfo();
        model.setReasoning(true);
        model.setToolCall(true);
        model.setAttachment(true);
        model.setTemperature(true);
        model.setOpenWeights(true);

        assertThat(model.supportsReasoning()).isTrue();
        assertThat(model.supportsToolCalls()).isTrue();
        assertThat(model.supportsAttachments()).isTrue();
        assertThat(model.supportsTemperature()).isTrue();
        assertThat(model.hasOpenWeights()).isTrue();
    }

    @Test
    void multimodal_logic() {
        ModelInfo model = new ModelInfo();

        // No modalities -> false
        assertThat(model.isMultimodal()).isFalse();

        Modalities m1 = new Modalities();
        m1.setInput(List.of("text"));
        model.setModalities(m1);
        assertThat(model.isMultimodal()).isFalse(); // only text

        Modalities m2 = new Modalities();
        m2.setInput(List.of("text", "image"));
        model.setModalities(m2);
        assertThat(model.isMultimodal()).isTrue(); // multiple inputs

        Modalities m3 = new Modalities();
        m3.setInput(List.of("image"));
        model.setModalities(m3);
        assertThat(model.isMultimodal()).isTrue(); // single non-text
    }

    @Test
    void free_model_logic() {
        ModelInfo model = new ModelInfo();

        Cost freeCost = new Cost();
        freeCost.setInput(0.0);
        freeCost.setOutput(0.0);
        model.setCost(freeCost);

        assertThat(model.isFree()).isTrue();

        Cost paidCost = new Cost();
        paidCost.setInput(0.1);
        paidCost.setOutput(0.2);
        model.setCost(paidCost);

        assertThat(model.isFree()).isFalse();
    }

    @Test
    void equals_and_hashcode() {
        ModelInfo m1 = new ModelInfo();
        m1.setId("gpt-4");

        ModelInfo m2 = new ModelInfo();
        m2.setId("gpt-4");

        ModelInfo m3 = new ModelInfo();
        m3.setId("gpt-3");

        assertThat(m1).isEqualTo(m2).hasSameHashCodeAs(m2).isNotEqualTo(m3).doesNotHaveSameHashCodeAs(m3);

        assertThat(m1).isNotEqualTo(null).isNotEqualTo(new Object());
    }
}
