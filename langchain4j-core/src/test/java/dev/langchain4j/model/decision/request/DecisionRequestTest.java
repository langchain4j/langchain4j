package dev.langchain4j.model.decision.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void should_accept_text_and_map_input() {

        assertThat(DecisionRequest.builder()
                        .input("Buy now!")
                        .question("spam", QUESTION)
                        .build()
                        .input())
                .isEqualTo("Buy now!");
        assertThat(DecisionRequest.builder()
                        .input(Map.of("subject", "Buy now!", "tags", List.of("promo")))
                        .question("spam", QUESTION)
                        .build()
                        .input())
                .isEqualTo(Map.of("subject", "Buy now!", "tags", List.of("promo")));
    }

    @Test
    void should_reject_blank_or_empty_input() {

        assertThatThrownBy(() -> DecisionRequest.builder()
                        .input(" ")
                        .question("spam", QUESTION)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");
        assertThatThrownBy(() -> DecisionRequest.builder()
                        .input(Map.of())
                        .question("spam", QUESTION)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");
    }

    @Test
    void should_reject_missing_input() {

        assertThatThrownBy(() -> DecisionRequest.builder().question("spam", QUESTION).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");
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
                DecisionRequest.builder().input(input).questions(questions).build();
        input.put("body", "Limited offer");
        questions.put("phishing", QUESTION);

        assertThat(request.input()).isEqualTo(Map.of("subject", "Buy now!"));
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
                        .input(Map.of("customer", new Customer("Anna", Plan.ENTERPRISE, 3, List.of("vip"))))
                        .question("spam", QUESTION)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input can only contain strings, numbers, booleans, nulls, maps and lists");

        assertThatThrownBy(() -> DecisionRequest.builder()
                        .input(Map.of("plan", Plan.ENTERPRISE))
                        .question("spam", QUESTION)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan");
    }

    @Test
    void normalized_input_should_be_deeply_immutable() {

        List<String> tags = new ArrayList<>(List.of("vip"));
        Map<String, Object> customer = new HashMap<>(Map.of("tags", tags));

        DecisionRequest request = DecisionRequest.builder()
                .input(Map.of("customer", customer))
                .question("spam", QUESTION)
                .build();
        tags.add("new");

        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedCustomer =
                (Map<String, Object>) ((Map<String, Object>) request.input()).get("customer");
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
