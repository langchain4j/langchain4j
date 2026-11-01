package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultimodalAgentContextTest {

    private static final ImageContent IMAGE = ImageContent.from("https://example.com/llamas.png");

    public interface ImageDescriber {

        @UserMessage("Describe the image.")
        @Agent(outputKey = "description")
        String describe(@UserMessage @V("image") ImageContent image);
    }

    public interface ImageOnlyDescriber {

        @Agent(outputKey = "description")
        String describe(@UserMessage @V("image") ImageContent image);
    }

    public interface ContentsDescriber {

        @Agent(outputKey = "description")
        String describe(@UserMessage @V("contents") List<Content> contents);
    }

    public interface PoemWriter {

        @UserMessage("Write a poem about the described image.")
        @Agent(outputKey = "poem")
        String write();
    }

    @Test
    void should_add_the_context_to_a_multimodal_user_message() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Three llamas.");
        ImageDescriber describer = AgenticServices.agentBuilder(ImageDescriber.class)
                .chatModel(model)
                .context(scope -> "The user loves llamas.")
                .build();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(describer)
                .outputKey("description")
                .build();

        assertThat(workflow.invoke(Map.of("image", IMAGE))).isEqualTo("Three llamas.");

        assertThat(userMessage(model.request()).contents())
                .containsExactly(
                        TextContent.from("Considering this context \"The user loves llamas.\"\nDescribe the image."),
                        IMAGE);
    }

    @Test
    void should_add_the_context_to_a_user_message_without_text() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Three llamas.");
        ImageOnlyDescriber describer = AgenticServices.agentBuilder(ImageOnlyDescriber.class)
                .chatModel(model)
                .context(scope -> "The user loves llamas.")
                .build();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(describer)
                .outputKey("description")
                .build();

        assertThat(workflow.invoke(Map.of("image", IMAGE))).isEqualTo("Three llamas.");

        assertThat(userMessage(model.request()).contents())
                .containsExactly(TextContent.from("Considering this context \"The user loves llamas.\"\n"), IMAGE);
    }

    @Test
    void should_leave_a_user_message_without_text_unchanged_when_the_context_is_empty() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Three llamas.");
        ImageOnlyDescriber describer = AgenticServices.agentBuilder(ImageOnlyDescriber.class)
                .chatModel(model)
                .context(scope -> "")
                .build();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(describer)
                .outputKey("description")
                .build();

        assertThat(workflow.invoke(Map.of("image", IMAGE))).isEqualTo("Three llamas.");

        assertThat(userMessage(model.request()).contents()).containsExactly(IMAGE);
    }

    @Test
    void should_add_the_context_to_all_the_text_of_a_user_message_with_several_text_contents() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Three llamas.");
        ContentsDescriber describer = AgenticServices.agentBuilder(ContentsDescriber.class)
                .chatModel(model)
                .context(scope -> "The user loves llamas.")
                .build();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(describer)
                .outputKey("description")
                .build();

        List<Content> contents = List.of(TextContent.from("Describe"), IMAGE, TextContent.from("in one sentence."));
        assertThat(workflow.invoke(Map.of("contents", contents))).isEqualTo("Three llamas.");

        assertThat(userMessage(model.request()).contents())
                .containsExactly(
                        TextContent.from(
                                "Considering this context \"The user loves llamas.\"\nDescribe\nin one sentence."),
                        IMAGE);
    }

    @Test
    void should_mention_the_contents_of_a_user_message_without_text_in_the_summarized_context() {
        ChatModelMock describerModel = ChatModelMock.thatAlwaysResponds("Three llamas.");
        ImageOnlyDescriber describer = AgenticServices.agentBuilder(ImageOnlyDescriber.class)
                .name("describer")
                .chatModel(describerModel)
                .build();

        ChatModelMock writerModel = ChatModelMock.thatResponds(request -> AiMessage.from(
                userMessage(request).singleText().startsWith("Create a short summary")
                        ? "The describer saw three llamas."
                        : "A poem about llamas."));
        PoemWriter writer = AgenticServices.agentBuilder(PoemWriter.class)
                .name("writer")
                .chatModel(writerModel)
                .summarizedContext("describer")
                .build();

        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(describer, writer)
                .outputKey("poem")
                .build();

        assertThat(workflow.invoke(Map.of("image", IMAGE))).isEqualTo("A poem about llamas.");

        assertThat(userMessage(writerModel.requests().get(0)).singleText())
                .contains("User: \"[image]\"")
                .contains("describer agent: \"Three llamas.\"");
    }

    @Test
    void should_summarize_a_context_containing_a_multimodal_user_message() {
        ChatModelMock describerModel = ChatModelMock.thatAlwaysResponds("Three llamas.");
        ImageDescriber describer = AgenticServices.agentBuilder(ImageDescriber.class)
                .name("describer")
                .chatModel(describerModel)
                .build();

        ChatModelMock writerModel = ChatModelMock.thatResponds(request -> AiMessage.from(
                userMessage(request).singleText().startsWith("Create a short summary")
                        ? "The describer saw three llamas."
                        : "A poem about llamas."));
        PoemWriter writer = AgenticServices.agentBuilder(PoemWriter.class)
                .name("writer")
                .chatModel(writerModel)
                .summarizedContext("describer")
                .build();

        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(describer, writer)
                .outputKey("poem")
                .build();

        assertThat(workflow.invoke(Map.of("image", IMAGE))).isEqualTo("A poem about llamas.");

        assertThat(writerModel.requests()).hasSize(2);
        assertThat(userMessage(writerModel.requests().get(0)).singleText())
                .contains("User: \"Describe the image.\"")
                .contains("describer agent: \"Three llamas.\"");
        assertThat(userMessage(writerModel.requests().get(1)).singleText())
                .isEqualTo("Considering this context \"The describer saw three llamas.\"\n"
                        + "Write a poem about the described image.");
    }

    private static dev.langchain4j.data.message.UserMessage userMessage(ChatRequest request) {
        return dev.langchain4j.data.message.UserMessage.findLast(request.messages())
                .orElseThrow();
    }
}
