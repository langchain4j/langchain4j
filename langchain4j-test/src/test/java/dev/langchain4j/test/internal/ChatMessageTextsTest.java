package dev.langchain4j.test.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

class ChatMessageTextsTest {

    @Test
    void should_not_cut_a_text_of_exactly_the_maximum_length() {
        assertThat(ChatMessageTexts.truncate("a".repeat(1000), 1000)).isEqualTo("a".repeat(1000));
        assertThat(ChatMessageTexts.truncate("a".repeat(300), 300)).isEqualTo("a".repeat(300));
    }

    @Test
    void should_cut_a_longer_text_and_say_how_many_characters_were_cut() {
        assertThat(ChatMessageTexts.truncate("a".repeat(1001), 1000)).isEqualTo("a".repeat(1000) + "...(1 more characters)");
    }

    @Test
    void should_not_split_a_character_made_of_two_chars() {
        String text = "a".repeat(9) + "😀" + "b";

        String truncated = ChatMessageTexts.truncate(text, 10);

        assertThat(truncated).isEqualTo("a".repeat(9) + "...(3 more characters)");
        assertThat(Character.isHighSurrogate(truncated.charAt(8))).isFalse();
    }

    @Test
    void should_send_an_image_without_a_chosen_detail_level_to_the_judge_with_auto_detail() {
        ImageContent notChosen = ImageContent.from("https://example.com/a.png");
        ImageContent chosen = ImageContent.from("https://example.com/b.png", ImageContent.DetailLevel.LOW);

        assertThat(((ImageContent) ChatMessageTexts.forJudge(notChosen)).detailLevel())
                .isEqualTo(ImageContent.DetailLevel.AUTO);
        assertThat(ChatMessageTexts.forJudge(chosen)).isSameAs(chosen);
    }

    @Test
    void should_return_a_message_without_images_to_change_as_it_is() {
        UserMessage text = UserMessage.from("Hi");
        AiMessage answer = AiMessage.from("Hello");

        assertThat(ChatMessageTexts.forJudge(text)).isSameAs(text);
        assertThat(ChatMessageTexts.forJudge(answer)).isSameAs(answer);
    }

    @Test
    void should_show_the_tool_call_id_when_there_is_one() {
        assertThat(ChatMessageTexts.text(AiMessage.from(toolCall("call_7"))))
                .isEqualTo("[calls tool getWeather (id call_7) with {\"city\":\"Paris\"}]");
        assertThat(ChatMessageTexts.text(toolResult("call_7", false)))
                .isEqualTo("[result of tool getWeather (id call_7)] sunny");
        assertThat(ChatMessageTexts.text(toolResult("call_7", true)))
                .isEqualTo("[error result of tool getWeather (id call_7)] sunny");
    }

    @Test
    void should_leave_out_a_blank_or_missing_tool_call_id() {
        for (String id : new String[] {null, "", "  "}) {
            assertThat(ChatMessageTexts.text(AiMessage.from(toolCall(id))))
                    .isEqualTo("[calls tool getWeather with {\"city\":\"Paris\"}]");
            assertThat(ChatMessageTexts.text(toolResult(id, false))).isEqualTo("[result of tool getWeather] sunny");
            assertThat(ChatMessageTexts.text(toolResult(id, true)))
                    .isEqualTo("[error result of tool getWeather] sunny");
        }
    }

    private static ToolExecutionRequest toolCall(String id) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("getWeather")
                .arguments("{\"city\":\"Paris\"}")
                .build();
    }

    private static ToolExecutionResultMessage toolResult(String id, boolean isError) {
        return ToolExecutionResultMessage.builder()
                .id(id)
                .toolName("getWeather")
                .text("sunny")
                .isError(isError)
                .build();
    }
}
