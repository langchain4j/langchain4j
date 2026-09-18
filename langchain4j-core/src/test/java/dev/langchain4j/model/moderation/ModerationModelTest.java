package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ModerationModelTest implements WithAssertions {

    public static class FlagEverythingModel implements ModerationModel {

        @Override
        public ModerationResponse doModerate(ModerationRequest moderationRequest) {
            String flaggedText = moderationRequest.texts().get(0);
            return ModerationResponse.builder()
                    .moderation(Moderation.flagged(flaggedText))
                    .build();
        }
    }

    public static class FlagEverythingWithMetadataModel implements ModerationModel {

        @Override
        public ModerationResponse doModerate(ModerationRequest moderationRequest) {
            String flaggedText = moderationRequest.texts().get(0);
            return ModerationResponse.builder()
                    .moderation(Moderation.flagged(flaggedText))
                    .metadata(Map.of("provider", "test"))
                    .typedMetadata(new NonMappableMetadata())
                    .build();
        }
    }

    @Test
    void moderate_prompt() {
        ModerationModel model = new FlagEverythingModel();
        Response<Moderation> response = model.moderate(Prompt.from("Hello, world!"));
        assertThat(response).isEqualTo(Response.from(Moderation.flagged("Hello, world!")));
    }

    @Test
    void moderate_chat_message() {
        ModerationModel model = new FlagEverythingModel();
        Response<Moderation> response = model.moderate(UserMessage.from("Hello, world!"));
        assertThat(response).isEqualTo(Response.from(Moderation.flagged("Hello, world!")));
    }

    @Test
    void moderate_text_segment() {
        ModerationModel model = new FlagEverythingModel();
        Response<Moderation> response = model.moderate(TextSegment.from("Hello, world!"));
        assertThat(response).isEqualTo(Response.from(Moderation.flagged("Hello, world!")));
    }

    @Test
    void moderate_text_with_legacy_metadata() {
        ModerationModel model = new FlagEverythingWithMetadataModel();
        Response<Moderation> response = model.moderate("Hello, world!");
        assertThat(response.content()).isEqualTo(Moderation.flagged("Hello, world!"));
        assertThat(response.metadata()).containsEntry("provider", "test");
    }

    @Test
    void moderate_moderation_request_with_text() {
        ModerationModel model = new FlagEverythingModel();
        ModerationRequest request =
                ModerationRequest.builder().texts(List.of("hello")).build();
        ModerationResponse response = model.moderate(request);
        assertThat(response.moderation()).isEqualTo(Moderation.flagged("hello"));
    }

    @Test
    void moderate_moderation_request_with_messages() {
        ModerationModel model = new FlagEverythingModel();
        ModerationRequest request =
                ModerationRequest.builder().texts(List.of("user msg")).build();
        ModerationResponse response = model.moderate(request);
        assertThat(response.moderation()).isEqualTo(Moderation.flagged("user msg"));
    }

    private static class NonMappableMetadata implements ModerationResponseMetadata {}
}
