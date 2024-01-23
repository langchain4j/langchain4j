package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@SuppressWarnings("deprecation")
class ModerationModelTest implements WithAssertions {
    public static class FlagEverythingModel implements ModerationModel {
        @Override
        public Response<Moderation> moderate(String text) {
            return Response.from(Moderation.flagged(text));
        }

        @Override
        public Response<Moderation> moderate(List<ChatMessage> messages) {
            return Response.from(Moderation.flagged(messages.get(0).text()));
        }
    }

    @Test
    public void test_moderate_prompt() {
        ModerationModel model = new FlagEverythingModel();
        Response<Moderation> response = model.moderate(Prompt.from("Hello, world!"));
        assertThat(response).isEqualTo(Response.from(Moderation.flagged("Hello, world!")));
    }

    @Test
    public void test_moderate_chat_message() {
        ModerationModel model = new FlagEverythingModel();
        Response<Moderation> response = model.moderate(
                UserMessage.from("Hello, world!"));
        assertThat(response).isEqualTo(Response.from(Moderation.flagged("Hello, world!")));
    }

    @Test
    public void test_moderate_TextSegment() {
        ModerationModel model = new FlagEverythingModel();
        Response<Moderation> response = model.moderate(
                TextSegment.from("Hello, world!"));
        assertThat(response).isEqualTo(Response.from(Moderation.flagged("Hello, world!")));
    }
}