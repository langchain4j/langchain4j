package dev.langchain4j.model.openai.internal.audio.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpenAiVoiceConsentIdTest {

    @Test
    void should_build_consent_id() {
        OpenAiVoiceConsentId id =
                OpenAiVoiceConsentId.builder().consentId("cons_123abc").build();

        assertThat(id.consentId()).isEqualTo("cons_123abc");
    }

    @Test
    void should_reject_null_consent_id_from_builder() {
        OpenAiVoiceConsentId.Builder builder = OpenAiVoiceConsentId.builder();

        assertThatThrownBy(builder::build)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("consentId cannot be null");
    }

    @Test
    void should_reject_null_consent_id_from_constructor() {
        assertThatThrownBy(() -> new OpenAiVoiceConsentId((String) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("consentId cannot be null");
    }
}
