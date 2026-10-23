package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Scale;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersistedDecisionAnswersTest {

    enum Quality {
        POOR,
        FAIR,
        GOOD,
        EXCELLENT
    }

    @Test
    void answers_of_decision_agents_are_persisted_with_their_enum_type_and_probabilities() {
        Choice<Quality> choice = Choice.<Quality>builder()
                .value(Quality.GOOD)
                .probabilities(Map.of(Quality.GOOD, 0.7, Quality.FAIR, 0.3))
                .confidence(0.9)
                .build();
        Scale<Quality> scale = Scale.builder(Quality.class)
                .mean(2.4)
                .probabilities(Map.of(Quality.GOOD, 0.6, Quality.EXCELLENT, 0.4))
                .build();
        YesNoAnswer yesNo = YesNoAnswer.from(0.42);

        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        scope.writeState("choice", choice);
        scope.writeState("scale", scale);
        scope.writeState("yesNo", yesNo);
        scope.registerAgentInvocation(
                new AgentInvocation(String.class, "scorer", "scorer$0", Map.of("choice", choice), scale), null);

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(json);

        // the codec writes the type of the enum next to one of its constants, and resolves it when reading
        assertThat(json).contains("\"value\":[\"" + Quality.class.getName() + "\",\"GOOD\"]");
        assertThat(restored.readState("choice")).isEqualTo(choice);
        assertThat(restored.readState("scale")).isEqualTo(scale);
        assertThat(restored.readState("yesNo")).isEqualTo(yesNo);
        assertThat(restored.agentInvocations()).singleElement().satisfies(invocation -> {
            assertThat(invocation.input()).containsEntry("choice", choice);
            assertThat(invocation.output()).isEqualTo(scale);
        });
        // the scope that was written is left untouched
        assertThat(scope.readState("choice")).isSameAs(choice);
    }

    record ConfidentReview(Choice<Quality> quality, YesNoAnswer publish) {}

    record PlainReview(Quality quality, boolean publish) {}

    static class ConfidenceReview {
        Choice<Quality> quality;
    }

    static class InheritedConfidentReview extends ConfidenceReview {
        boolean publish;
    }

    @Test
    void only_an_object_with_fields_of_the_answer_types_is_a_composite_answer() {
        assertThat(PersistedDecisionAnswers.isCompositeAnswer(new ConfidentReview(null, null)))
                .isTrue();
        // a field inherited from a superclass counts, as DecisionMethod fills it when it builds the answer
        assertThat(PersistedDecisionAnswers.isCompositeAnswer(new InheritedConfidentReview()))
                .isTrue();
        assertThat(PersistedDecisionAnswers.isCompositeAnswer(new PlainReview(Quality.GOOD, true)))
                .isFalse();
        assertThat(PersistedDecisionAnswers.isCompositeAnswer(YesNoAnswer.from(0.5)))
                .isFalse();
        assertThat(PersistedDecisionAnswers.isCompositeAnswer("a string")).isFalse();
        assertThat(PersistedDecisionAnswers.isCompositeAnswer(null)).isFalse();
    }

    @Test
    void an_answer_inheriting_its_fields_is_dropped_from_the_persisted_scope() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        scope.writeState("review", new InheritedConfidentReview());
        scope.writeState("story", "Once upon a time");

        // the object is dropped when the scope is saved instead of failing when it is loaded, as its Choice
        // field has no form the codec could read back
        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson(scope));

        assertThat(restored.readState("review")).isNull();
        assertThat(restored.readState("story")).isEqualTo("Once upon a time");
    }

    @Test
    void a_scale_without_probabilities_is_restored_from_its_mean() {
        Scale<Quality> scale = Scale.builder(Quality.class).mean(2.4).build();

        Object persisted = PersistedDecisionAnswers.toPersisted(scale);

        assertThat(persisted).isInstanceOf(PersistedDecisionAnswers.PersistedScale.class);
        assertThat(PersistedDecisionAnswers.fromPersisted(persisted)).isEqualTo(scale);
    }

    @Test
    void a_scope_without_answers_of_decision_agents_is_persisted_as_recorded() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        scope.writeState("story", "Once upon a time");
        AgentInvocation invocation = new AgentInvocation(
                String.class, "writer", "writer$0", new HashMap<>(Map.of("topic", "dragons")), "story");
        scope.registerAgentInvocation(invocation, null);

        String json = AgenticScopeSerializer.toJson(scope);

        // the invocation is not rebuilt by the conversion, so its input map keeps its type in the document
        assertThat(json).contains("\"input\":[\"java.util.HashMap\"");
        assertThat(AgenticScopeSerializer.fromJson(json).agentInvocations())
                .singleElement()
                .isEqualTo(invocation);
    }
}
