package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ModerationModelTest implements WithAssertions {

    public static class FlagEverythingModel implements ModerationModel {

        @Override
        public ModerationResponse doModerate(ModerationRequest moderationRequest) {
            String flaggedText;
            if (moderationRequest.hasText()) {
                flaggedText = moderationRequest.text();
            } else {
                flaggedText = ((UserMessage) moderationRequest.messages().get(0)).singleText();
            }
            return ModerationResponse.builder()
                    .moderation(Moderation.flagged(flaggedText))
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
    void moderate_moderation_request_with_text() {
        ModerationModel model = new FlagEverythingModel();
        ModerationRequest request = ModerationRequest.builder().text("hello").build();
        ModerationResponse response = model.moderate(request);
        assertThat(response.moderation()).isEqualTo(Moderation.flagged("hello"));
    }

    @Test
    void moderate_moderation_request_with_messages() {
        ModerationModel model = new FlagEverythingModel();
        ModerationRequest request = ModerationRequest.builder()
                .messages(List.of(UserMessage.from("user msg")))
                .build();
        ModerationResponse response = model.moderate(request);
        assertThat(response.moderation()).isEqualTo(Moderation.flagged("user msg"));
    }
}
