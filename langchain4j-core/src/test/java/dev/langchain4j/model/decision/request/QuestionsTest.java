package dev.langchain4j.model.decision.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionsTest {

    @Test
    void should_create_noul_question_without_criteria() {

        NoulQuestion question =
                NoulQuestion.builder().instructions("Is this spam?").build();

        assertThat(question.instructions()).isEqualTo("Is this spam?");
        assertThat(question.whenTrue()).isNull();
        assertThat(question.whenFalse()).isNull();
    }

    @Test
    void should_create_noul_question_with_text_and_structured_criteria() {

        NoulQuestion question = NoulQuestion.builder()
                .instructions("Does the customer ask for a refund?")
                .whenTrue("The customer explicitly asks for their money back")
                .whenFalse(Map.of("what", "A question about a charge", "examples", List.of("Why was I charged?")))
                .build();

        assertThat(question.whenTrue()).isEqualTo("The customer explicitly asks for their money back");
        assertThat(question.whenFalse())
                .isEqualTo(Map.of("what", "A question about a charge", "examples", List.of("Why was I charged?")));
    }

    @Test
    void should_reject_invalid_noul_criteria() {

        assertThatThrownBy(() -> NoulQuestion.builder()
                        .instructions("Is this spam?")
                        .whenTrue(" ")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whenTrue");

        assertThatThrownBy(() -> NoulQuestion.builder()
                        .instructions("Is this spam?")
                        .whenFalse(42)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whenFalse");
    }

    @Test
    void should_require_instructions() {

        assertThatThrownBy(() -> NoulQuestion.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions");

        assertThatThrownBy(() -> ChoiceQuestion.builder()
                        .option("a", "A")
                        .option("b", "B")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions");

        assertThatThrownBy(() -> ScoreQuestion.builder().level("low").level("high").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions");
    }

    @Test
    void should_create_choice_question_keeping_option_order() {

        ChoiceQuestion question = ChoiceQuestion.builder()
                .instructions("Which team should handle this ticket?")
                .option("support", Map.of("what", "Problems using the product", "not_for", "Invoices"))
                .option("billing", "Payments, invoices, refunds")
                .option("sales", List.of("Pricing", "Upgrades"))
                .build();

        assertThat(question.options().keySet()).containsExactly("support", "billing", "sales");
        assertThat(question.options().get("billing")).isEqualTo("Payments, invoices, refunds");
        assertThat(question.options().get("support"))
                .isEqualTo(Map.of("what", "Problems using the product", "not_for", "Invoices"));
    }

    @Test
    void should_replace_choice_options() {

        ChoiceQuestion question = ChoiceQuestion.builder()
                .instructions("Which team?")
                .option("old", "Old team")
                .options(Map.of("billing", "Payments", "support", "Bugs"))
                .build();

        assertThat(question.options()).containsOnlyKeys("billing", "support");
    }

    @Test
    void should_reject_invalid_choice_options() {

        assertThatThrownBy(() -> ChoiceQuestion.builder()
                        .instructions("Which team?")
                        .option("billing", "Payments")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 options");

        assertThatThrownBy(() -> ChoiceQuestion.builder().option(" ", "Payments"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("option name");

        assertThatThrownBy(() -> ChoiceQuestion.builder().option("billing", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criteria");

        assertThatThrownBy(() -> ChoiceQuestion.builder().option("billing", (Object) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criteria");
    }

    @Test
    void should_create_score_question_keeping_level_order() {

        ScoreQuestion question = ScoreQuestion.builder()
                .instructions("How frustrated is the customer?")
                .level("Calm")
                .level("Frustrated")
                .level(Map.of("what", "Angry", "examples", List.of("This is unacceptable!")))
                .build();

        assertThat(question.levels())
                .containsExactly(
                        "Calm", "Frustrated", Map.of("what", "Angry", "examples", List.of("This is unacceptable!")));
    }

    @Test
    void should_replace_score_levels() {

        ScoreQuestion question = ScoreQuestion.builder()
                .instructions("How urgent?")
                .level("old")
                .levels(List.of("Can wait", "This week", "Now"))
                .build();

        assertThat(question.levels()).containsExactly("Can wait", "This week", "Now");
    }

    @Test
    void should_reject_invalid_score_levels() {

        assertThatThrownBy(() -> ScoreQuestion.builder()
                        .instructions("How urgent?")
                        .level("Now")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 levels");

        assertThatThrownBy(() -> ScoreQuestion.builder().level(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level");
    }

    @Test
    void questions_should_be_immutable() {

        ChoiceQuestion choice = ChoiceQuestion.builder()
                .instructions("Which team?")
                .option("billing", "Payments")
                .option("support", "Bugs")
                .build();
        ScoreQuestion score = ScoreQuestion.builder()
                .instructions("How urgent?")
                .level("Low")
                .level("High")
                .build();

        assertThatThrownBy(() -> choice.options().put("sales", "Pricing"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> score.levels().add("Critical")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_implement_equals_and_hash_code() {

        assertThat(NoulQuestion.builder().instructions("Spam?").whenTrue("Ads").build())
                .isEqualTo(NoulQuestion.builder().instructions("Spam?").whenTrue("Ads").build())
                .hasSameHashCodeAs(
                        NoulQuestion.builder().instructions("Spam?").whenTrue("Ads").build())
                .isNotEqualTo(
                        NoulQuestion.builder().instructions("Spam?").whenFalse("Ads").build());

        assertThat(ChoiceQuestion.builder()
                        .instructions("Team?")
                        .option("a", "A")
                        .option("b", "B")
                        .build())
                .isEqualTo(ChoiceQuestion.builder()
                        .instructions("Team?")
                        .option("a", "A")
                        .option("b", "B")
                        .build())
                .isNotEqualTo(ChoiceQuestion.builder()
                        .instructions("Team?")
                        .option("a", "A")
                        .option("c", "C")
                        .build());

        assertThat(ScoreQuestion.builder()
                        .instructions("Urgency?")
                        .level("Low")
                        .level("High")
                        .build())
                .isEqualTo(ScoreQuestion.builder()
                        .instructions("Urgency?")
                        .level("Low")
                        .level("High")
                        .build())
                .isNotEqualTo(ScoreQuestion.builder()
                        .instructions("Urgency?")
                        .level("High")
                        .level("Low")
                        .build());
    }
}
