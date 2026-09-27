package dev.langchain4j.model.decision.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionsTest {

    @Test
    void should_create_yes_no_question_without_criteria() {

        YesNoQuestion question =
                YesNoQuestion.builder().text("Is this spam?").build();

        assertThat(question.text()).isEqualTo("Is this spam?");
        assertThat(question.yesWhen()).isNull();
        assertThat(question.noWhen()).isNull();
    }

    @Test
    void should_create_yes_no_question_with_text_and_structured_criteria() {

        YesNoQuestion question = YesNoQuestion.builder()
                .text("Does the customer ask for a refund?")
                .yesWhen("The customer explicitly asks for their money back")
                .noWhen(Map.of("what", "A question about a charge", "examples", List.of("Why was I charged?")))
                .build();

        assertThat(question.yesWhen()).isEqualTo("The customer explicitly asks for their money back");
        assertThat(question.noWhen())
                .isEqualTo(Map.of("what", "A question about a charge", "examples", List.of("Why was I charged?")));
    }

    @Test
    void should_reject_invalid_yes_no_criteria() {

        assertThatThrownBy(() -> YesNoQuestion.builder()
                        .text("Is this spam?")
                        .yesWhen(" ")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yesWhen");

        assertThatThrownBy(() -> YesNoQuestion.builder()
                        .text("Is this spam?")
                        .noWhen(42)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("noWhen");
    }

    @Test
    void should_require_text() {

        assertThatThrownBy(() -> YesNoQuestion.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");

        assertThatThrownBy(() -> ChoiceQuestion.builder()
                        .option("a", "A")
                        .option("b", "B")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");

        assertThatThrownBy(() -> ScaleQuestion.builder().level("low").level("high").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void should_create_choice_question_keeping_option_order() {

        ChoiceQuestion question = ChoiceQuestion.builder()
                .text("Which team should handle this ticket?")
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
                .text("Which team?")
                .option("old", "Old team")
                .options(Map.of("billing", "Payments", "support", "Bugs"))
                .build();

        assertThat(question.options()).containsOnlyKeys("billing", "support");
    }

    @Test
    void should_reject_invalid_choice_options() {

        assertThatThrownBy(() -> ChoiceQuestion.builder()
                        .text("Which team?")
                        .option("billing", "Payments")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 options");

        assertThatThrownBy(() -> ChoiceQuestion.builder().option(" ", "Payments"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("option name");

        assertThatThrownBy(() -> ChoiceQuestion.builder().option("billing", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");

        assertThatThrownBy(() -> ChoiceQuestion.builder().option("billing", (Object) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void should_create_scale_question_keeping_level_order() {

        ScaleQuestion question = ScaleQuestion.builder()
                .text("How frustrated is the customer?")
                .level("Calm")
                .level("Frustrated")
                .level(Map.of("what", "Angry", "examples", List.of("This is unacceptable!")))
                .build();

        assertThat(question.levels())
                .containsExactly(
                        "Calm", "Frustrated", Map.of("what", "Angry", "examples", List.of("This is unacceptable!")));
    }

    @Test
    void should_replace_scale_levels() {

        ScaleQuestion question = ScaleQuestion.builder()
                .text("How urgent?")
                .level("old")
                .levels(List.of("Can wait", "This week", "Now"))
                .build();

        assertThat(question.levels()).containsExactly("Can wait", "This week", "Now");
    }

    @Test
    void should_reject_invalid_scale_levels() {

        assertThatThrownBy(() -> ScaleQuestion.builder()
                        .text("How urgent?")
                        .level("Now")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 levels");

        assertThatThrownBy(() -> ScaleQuestion.builder().level(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level");
    }

    @Test
    void questions_should_be_immutable() {

        ChoiceQuestion choice = ChoiceQuestion.builder()
                .text("Which team?")
                .option("billing", "Payments")
                .option("support", "Bugs")
                .build();
        ScaleQuestion score = ScaleQuestion.builder()
                .text("How urgent?")
                .level("Low")
                .level("High")
                .build();

        assertThatThrownBy(() -> choice.options().put("sales", "Pricing"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> score.levels().add("Critical")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_implement_equals_and_hash_code() {

        assertThat(YesNoQuestion.builder().text("Spam?").yesWhen("Ads").build())
                .isEqualTo(YesNoQuestion.builder().text("Spam?").yesWhen("Ads").build())
                .hasSameHashCodeAs(
                        YesNoQuestion.builder().text("Spam?").yesWhen("Ads").build())
                .isNotEqualTo(
                        YesNoQuestion.builder().text("Spam?").noWhen("Ads").build());

        assertThat(ChoiceQuestion.builder()
                        .text("Team?")
                        .option("a", "A")
                        .option("b", "B")
                        .build())
                .isEqualTo(ChoiceQuestion.builder()
                        .text("Team?")
                        .option("a", "A")
                        .option("b", "B")
                        .build())
                .isNotEqualTo(ChoiceQuestion.builder()
                        .text("Team?")
                        .option("a", "A")
                        .option("c", "C")
                        .build());

        assertThat(ScaleQuestion.builder()
                        .text("Urgency?")
                        .level("Low")
                        .level("High")
                        .build())
                .isEqualTo(ScaleQuestion.builder()
                        .text("Urgency?")
                        .level("Low")
                        .level("High")
                        .build())
                .isNotEqualTo(ScaleQuestion.builder()
                        .text("Urgency?")
                        .level("High")
                        .level("Low")
                        .build());
    }
}
