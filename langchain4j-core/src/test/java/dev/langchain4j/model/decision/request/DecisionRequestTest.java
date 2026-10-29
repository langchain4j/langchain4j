package dev.langchain4j.model.decision.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionRequestTest {

    private static final YesNoQuestion QUESTION =
            YesNoQuestion.builder().text("Is this spam?").build();

    @Test
    void should_accept_text_and_fields_input() {

        DecisionInput text = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", QUESTION)
                .build()
                .input();
        assertThat(text.type()).isEqualTo(DecisionInputType.TEXT);
        assertThat(((TextInput) text).text()).isEqualTo("Buy now!");

        DecisionInput fields = DecisionRequest.builder()
                .inputFields(Map.of("subject", "Buy now!", "tags", List.of("promo")))
                .question("spam", QUESTION)
                .build()
                .input();
        assertThat(fields.type()).isEqualTo(DecisionInputType.FIELDS);
        assertThat(((FieldsInput) fields).fields()).isEqualTo(Map.of("subject", "Buy now!", "tags", List.of("promo")));
    }

    @Test
    void should_accept_contents_as_input() {

        List<Content> contents =
                List.of(TextContent.from("Is this damaged?"), ImageContent.from("iVBORw0KGgo=", "image/png"));

        DecisionInput input = DecisionRequest.builder()
                .inputContents(contents)
                .question("damaged", QUESTION)
                .build()
                .input();

        assertThat(input.type()).isEqualTo(DecisionInputType.CONTENTS);
        assertThat(((ContentsInput) input).contents()).isEqualTo(contents);
    }

    @Test
    void should_accept_contents_as_values_of_fields() {

        ImageContent photo = ImageContent.from("iVBORw0KGgo=", "image/png");
        Map<String, Object> fields = Map.of("comment", "Arrived like this", "photo", photo, "photos", List.of(photo));

        assertThat(FieldsInput.from(fields).fields()).isEqualTo(fields);
        assertThatThrownBy(() -> FieldsInput.from(Map.of("ticket", Map.of("photo", photo))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("as values of a field, Contents or lists of Contents");
        assertThatThrownBy(() -> FieldsInput.from(Map.of("photos", List.of(photo, "a photo of the box"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("as values of a field, Contents or lists of Contents");
        assertThat(FieldsInput.from(Map.of("photos", List.of())).fields()).isEqualTo(Map.of("photos", List.of()));
    }

    @Test
    void should_reject_empty_contents_or_null_content() {

        assertThatThrownBy(() -> ContentsInput.from(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contents cannot be null or empty");

        List<Content> withNull = new ArrayList<>();
        withNull.add(TextContent.from("Buy now!"));
        withNull.add(null);
        assertThatThrownBy(() -> ContentsInput.from(withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contents cannot contain null");
    }

    @Test
    void should_accept_messages_as_input() {

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getOrder")
                .arguments("{\"id\":42}")
                .build();
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a billing support agent."),
                UserMessage.from("I was charged twice for order 42."),
                AiMessage.from(toolCall),
                ToolExecutionResultMessage.from(toolCall, "{\"charges\":[19.99,19.99]}"),
                AiMessage.from("You were charged twice, I have refunded one charge."));

        DecisionInput input = DecisionRequest.builder()
                .inputMessages(messages)
                .question("resolved", QUESTION)
                .build()
                .input();

        assertThat(input.type()).isEqualTo(DecisionInputType.MESSAGES);
        assertThat(((MessagesInput) input).messages()).isEqualTo(messages);
    }

    @Test
    void should_copy_messages_and_contents() {

        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("Hi")));
        List<Content> contents = new ArrayList<>(List.of(TextContent.from("Hi")));

        MessagesInput messagesInput = MessagesInput.from(messages);
        ContentsInput contentsInput = ContentsInput.from(contents);
        messages.add(AiMessage.from("Hello"));
        contents.add(TextContent.from("Hello"));

        assertThat(messagesInput.messages()).containsExactly(UserMessage.from("Hi"));
        assertThat(contentsInput.contents()).containsExactly(TextContent.from("Hi"));
        assertThatThrownBy(() -> messagesInput.messages().add(AiMessage.from("Hello")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> contentsInput.contents().add(TextContent.from("Hello")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_reject_empty_messages_or_null_message() {

        assertThatThrownBy(() -> MessagesInput.from(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");

        List<ChatMessage> withNull = new ArrayList<>();
        withNull.add(UserMessage.from("Buy now!"));
        withNull.add(null);
        assertThatThrownBy(() -> MessagesInput.from(withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot contain null");
    }

    @Test
    void should_accept_custom_input() {

        DecisionInput custom = () -> DecisionInputType.TEXT;

        assertThat(DecisionRequest.builder()
                        .input(custom)
                        .question("spam", QUESTION)
                        .build()
                        .input())
                .isSameAs(custom);
    }

    @Test
    void last_input_should_replace_the_previous_one() {

        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .inputFields(Map.of("subject", "Buy now!"))
                .question("spam", QUESTION)
                .build();

        assertThat(request.input()).isEqualTo(FieldsInput.from(Map.of("subject", "Buy now!")));
        assertThat(request.toBuilder()
                        .inputMessages(List.of(UserMessage.from("Buy now!")))
                        .build()
                        .input())
                .isEqualTo(MessagesInput.from(List.of(UserMessage.from("Buy now!"))));
    }

    @Test
    void should_accept_messages_and_contents_as_varargs() {

        DecisionRequest messages = DecisionRequest.builder()
                .inputMessages(UserMessage.from("Hi"), AiMessage.from("Hello"))
                .question("spam", QUESTION)
                .build();
        DecisionRequest contents = DecisionRequest.builder()
                .inputContents(TextContent.from("Is this damaged?"), ImageContent.from("iVBORw0KGgo=", "image/png"))
                .question("damaged", QUESTION)
                .build();

        assertThat(messages.input())
                .isEqualTo(MessagesInput.from(List.of(UserMessage.from("Hi"), AiMessage.from("Hello"))));
        assertThat(contents.input())
                .isEqualTo(ContentsInput.from(
                        List.of(TextContent.from("Is this damaged?"), ImageContent.from("iVBORw0KGgo=", "image/png"))));
        assertThatThrownBy(() -> DecisionRequest.builder().inputMessages())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
        assertThatThrownBy(() -> DecisionRequest.builder().inputContents((Content[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contents cannot be null or empty");
    }

    @Test
    void should_reject_blank_or_empty_input() {

        assertThatThrownBy(() -> DecisionRequest.builder().input(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text cannot be null or blank");
        assertThatThrownBy(() -> DecisionRequest.builder().inputFields(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fields cannot be null or empty");
    }

    @Test
    void should_reject_missing_input() {

        assertThatThrownBy(() -> DecisionRequest.builder().question("spam", QUESTION).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("input must be set with input(String), inputFields(Map), inputContents(List), "
                        + "inputMessages(List) or input(DecisionInput)");
    }

    @Test
    void inputs_should_not_print_their_content() {

        assertThat(TextInput.from("My IBAN is DE89").toString()).doesNotContain("IBAN");
        assertThat(FieldsInput.from(Map.of("iban", "DE89")).toString()).doesNotContain("DE89");
        assertThat(ContentsInput.from(List.of(TextContent.from("My IBAN"))).toString())
                .doesNotContain("IBAN");
        assertThat(MessagesInput.from(List.of(UserMessage.from("My IBAN"))).toString())
                .doesNotContain("IBAN");
    }

    @Test
    void should_require_at_least_one_question() {

        assertThatThrownBy(() -> DecisionRequest.builder().input("Buy now!").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questions");
    }

    @Test
    void should_reject_duplicate_question_name() {

        assertThatThrownBy(() -> DecisionRequest.builder().question("spam", QUESTION).question("spam", QUESTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Question 'spam' is already defined");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_reject_blank_question_name(String name) {

        assertThatThrownBy(() -> DecisionRequest.builder().question(name, QUESTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question name");
    }

    @Test
    void should_keep_question_order() {

        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .question("c", QUESTION)
                .question("a", QUESTION)
                .question("b", QUESTION)
                .build();

        assertThat(request.questions()).containsOnlyKeys("c", "a", "b");
        assertThat(new ArrayList<>(request.questions().keySet())).containsExactly("c", "a", "b");
    }

    @Test
    void should_copy_input_and_questions() {

        Map<String, Object> input = new HashMap<>(Map.of("subject", "Buy now!"));
        Map<String, Question> questions = new LinkedHashMap<>(Map.of("spam", QUESTION));

        DecisionRequest request =
                DecisionRequest.builder().inputFields(input).questions(questions).build();
        input.put("body", "Limited offer");
        questions.put("phishing", QUESTION);

        assertThat(((FieldsInput) request.input()).fields()).isEqualTo(Map.of("subject", "Buy now!"));
        assertThat(request.questions()).containsOnlyKeys("spam");
        assertThatThrownBy(() -> request.questions().put("phishing", QUESTION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_default_to_empty_parameters() {

        DecisionRequest request =
                DecisionRequest.builder().input("Buy now!").question("spam", QUESTION).build();

        assertThat(request.parameters()).isEqualTo(DecisionRequestParameters.EMPTY);
        assertThat(request.modelName()).isNull();
    }

    @Test
    void should_expose_model_name_from_parameters() {

        DecisionRequest request = DecisionRequest.builder()
                .input("Buy now!")
                .question("spam", QUESTION)
                .parameters(DecisionRequestParameters.builder().modelName("model").build())
                .build();

        assertThat(request.modelName()).isEqualTo("model");
    }

    @Test
    void should_accept_custom_question_types() {

        record RankQuestion(String text, List<String> candidates) implements Question {}

        DecisionRequest request = DecisionRequest.builder()
                .input("Refactor the parser")
                .question("next_step", new RankQuestion("Which step is best?", List.of("test", "edit")))
                .build();

        assertThat(request.questions().get("next_step")).isInstanceOf(RankQuestion.class);
    }

    @Test
    void parameters_should_override() {

        DecisionRequestParameters defaults =
                DecisionRequestParameters.builder().modelName("default").build();

        assertThat(defaults.overrideWith(null)).isEqualTo(defaults);
        assertThat(defaults.overrideWith(DecisionRequestParameters.EMPTY).modelName())
                .isEqualTo("default");
        assertThat(defaults.overrideWith(DecisionRequestParameters.builder()
                                .modelName("override")
                                .build())
                        .modelName())
                .isEqualTo("override");
        assertThat(defaults.modelName()).isEqualTo("default");
    }

    enum Plan {
        ENTERPRISE
    }

    record Customer(String name, Plan plan, int openTickets, List<String> tags) {}

    @Test
    void should_reject_objects_in_input() {

        assertThatThrownBy(() -> DecisionRequest.builder()
                        .inputFields(Map.of("customer", new Customer("Anna", Plan.ENTERPRISE, 3, List.of("vip")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fields can only contain strings, numbers, booleans, nulls, maps and lists");

        assertThatThrownBy(() -> DecisionRequest.builder().inputFields(Map.of("plan", Plan.ENTERPRISE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan");
    }

    @Test
    void normalized_input_should_be_deeply_immutable() {

        List<String> tags = new ArrayList<>(List.of("vip"));
        Map<String, Object> customer = new HashMap<>(Map.of("tags", tags));

        DecisionRequest request = DecisionRequest.builder()
                .inputFields(Map.of("customer", customer))
                .question("spam", QUESTION)
                .build();
        tags.add("new");

        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedCustomer =
                (Map<String, Object>) ((FieldsInput) request.input()).fields().get("customer");
        assertThat(normalizedCustomer.get("tags")).isEqualTo(List.of("vip"));
        assertThatThrownBy(() -> normalizedCustomer.put("name", "Bob"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void to_string_should_not_contain_input() {

        DecisionRequest request = DecisionRequest.builder()
                .input("My IBAN is DE89 3704 0044 0532 0130 00")
                .question("spam", QUESTION)
                .build();

        assertThat(request.toString()).doesNotContain("IBAN").contains("questions");
    }
}
