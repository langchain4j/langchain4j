package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.decision.DecisionMocks.answeringYes;
import static dev.langchain4j.agentic.decision.DecisionMocks.choosing;
import static dev.langchain4j.agentic.decision.DecisionMocks.scaling;
import static dev.langchain4j.agentic.decision.DecisionMocks.triaging;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ChatMessagesAccess;
import dev.langchain4j.agentic.agent.DecisionResponseAccess;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.decision.DecisionAgents.AuditedCategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.CategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.ConfidentCategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.ConfidentTicketTriage;
import dev.langchain4j.agentic.decision.DecisionAgents.ConfidentTriage;
import dev.langchain4j.agentic.decision.DecisionAgents.CreditEligibility;
import dev.langchain4j.agentic.decision.DecisionAgents.MonitoredCategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.Quality;
import dev.langchain4j.agentic.decision.DecisionAgents.RequestCategory;
import dev.langchain4j.agentic.decision.DecisionAgents.Sentiment;
import dev.langchain4j.agentic.decision.DecisionAgents.SpamCheck;
import dev.langchain4j.agentic.decision.DecisionAgents.StyleScorer;
import dev.langchain4j.agentic.decision.DecisionAgents.Team;
import dev.langchain4j.agentic.decision.DecisionAgents.TicketTriage;
import dev.langchain4j.agentic.decision.DecisionAgents.TransactionGate;
import dev.langchain4j.agentic.decision.DecisionAgents.Triage;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.Scale;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DecisionAgentTest {

    private static final String LEGAL_REQUEST = "Can my landlord keep my deposit?";

    private static UntypedAgent sequenceOf(Object agent, String outputKey) {
        return AgenticServices.sequenceBuilder()
                .subAgents(agent)
                .outputKey(outputKey)
                .build();
    }

    @Test
    void should_answer_a_boolean_with_the_default_threshold() {
        TransactionGate gate = AgenticServices.decisionAgentBuilder(TransactionGate.class)
                .decisionModel(answeringYes(0.7))
                .build();

        Object suspicious = sequenceOf(gate, "suspicious").invoke(Map.of("transfer", "10 USD to the baker"));

        assertThat(suspicious).isEqualTo(true);
    }

    @Test
    void should_pass_a_boolean_answer_to_an_agent_reading_a_boxed_boolean() {
        TransactionGate gate = AgenticServices.decisionAgentBuilder(TransactionGate.class)
                .decisionModel(answeringYes(0.7))
                .build();
        Object review = AgenticServices.nonAiAgentBuilder(
                        scope -> scope.readState("suspicious", false) ? "review" : "proceed")
                .name("review")
                .inputKey(Boolean.class, "suspicious")
                .outputKey("decision")
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(gate, review)
                .outputKey("decision")
                .build();

        Object decision = sequence.invoke(Map.of("transfer", "50000 USD to an unknown account"));

        assertThat(decision).isEqualTo("review");
    }

    @Test
    void should_answer_a_boolean_with_a_fixed_threshold() {
        TransactionGate gate = AgenticServices.decisionAgentBuilder(TransactionGate.class)
                .decisionModel(answeringYes(0.7))
                .threshold(0.8)
                .build();

        Object suspicious = sequenceOf(gate, "suspicious").invoke(Map.of("transfer", "10 USD to the baker"));

        assertThat(suspicious).isEqualTo(false);
    }

    @Test
    void should_answer_a_boolean_with_a_threshold_provider_called_at_every_invocation() {
        AtomicInteger calls = new AtomicInteger();
        TransactionGate gate = AgenticServices.decisionAgentBuilder(TransactionGate.class)
                .decisionModel(answeringYes(0.7))
                .thresholdProvider(context -> {
                    assertThat(context.questionName()).isEqualTo("check");
                    assertThat(context.serviceInterface()).isEqualTo(TransactionGate.class);
                    return calls.incrementAndGet() == 1 ? 0.5 : 0.9;
                })
                .build();
        UntypedAgent sequence = sequenceOf(gate, "suspicious");

        assertThat(sequence.invoke(Map.of("transfer", "10 USD to the baker"))).isEqualTo(true);
        assertThat(sequence.invoke(Map.of("transfer", "10 USD to the baker"))).isEqualTo(false);
        assertThat(calls).hasValue(2);
    }

    @Test
    void should_answer_an_enum() {
        DecisionModelMock model = choosing("LEGAL", 0.9);
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(model)
                .build();

        Object category = sequenceOf(router, "category").invoke(Map.of("request", LEGAL_REQUEST));

        assertThat(category).isEqualTo(RequestCategory.LEGAL);
        assertThat(model.request().questions()).containsOnlyKeys("classify");
        assertThat(model.request().input()).isEqualTo(FieldsInput.from(Map.of("request", LEGAL_REQUEST)));
    }

    @Test
    void should_answer_a_yes_no_answer_with_its_probability() {
        SpamCheck spamCheck = AgenticServices.decisionAgentBuilder(SpamCheck.class)
                .decisionModel(answeringYes(0.42))
                .build();

        Object spam = sequenceOf(spamCheck, "spam").invoke(Map.of("message", "Buy now!"));

        assertThat(spam).isEqualTo(YesNoAnswer.from(0.42));
    }

    @Test
    void should_answer_a_choice_with_its_probabilities() {
        ConfidentCategoryRouter router = AgenticServices.decisionAgentBuilder(ConfidentCategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.7))
                .build();

        Object category = sequenceOf(router, "category").invoke(Map.of("request", LEGAL_REQUEST));

        assertThat(category).isInstanceOf(Choice.class);
        Choice<RequestCategory> choice = (Choice<RequestCategory>) category;
        assertThat(choice.value()).isEqualTo(RequestCategory.LEGAL);
        assertThat(choice.probabilityOf(RequestCategory.LEGAL)).isCloseTo(0.7, offset(0.001));
        assertThat(choice.probabilityOf(RequestCategory.MEDICAL)).isCloseTo(0.1, offset(0.001));
    }

    @Test
    void should_answer_a_scale_with_its_mean() {
        StyleScorer scorer = AgenticServices.decisionAgentBuilder(StyleScorer.class)
                .decisionModel(scaling("GOOD", 1.0))
                .build();

        Object score =
                sequenceOf(scorer, "styleScore").invoke(Map.of("story", "Once upon a time", "style", "fairy tale"));

        assertThat(score).isInstanceOf(Scale.class);
        Scale<Quality> scale = (Scale<Quality>) score;
        assertThat(scale.mostLikely()).isEqualTo(Quality.GOOD);
        assertThat(scale.mean()).isCloseTo(2.0, offset(0.001));
    }

    @Test
    void should_answer_the_questions_of_a_record_in_one_call() {
        DecisionModelMock model = triaging("BILLING", 0.8, "ANGRY");
        TicketTriage triage = AgenticServices.decisionAgentBuilder(TicketTriage.class)
                .decisionModel(model)
                .build();

        Object result = sequenceOf(triage, "triage").invoke(Map.of("ticket", "I was charged twice!"));

        assertThat(result).isEqualTo(new Triage(Team.BILLING, true, Sentiment.ANGRY));
        assertThat(model.requests()).hasSize(1);
        assertThat(model.request().questions()).containsOnlyKeys("team", "urgent", "sentiment");
    }

    @Test
    void should_answer_a_decision_result_with_the_response_of_the_model() {
        AuditedCategoryRouter router = AgenticServices.decisionAgentBuilder(AuditedCategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9).withTokenUsage(new TokenUsage(40, 2)))
                .build();

        Object result = sequenceOf(router, "category").invoke(Map.of("request", LEGAL_REQUEST));

        assertThat(result).isInstanceOf(DecisionResult.class);
        DecisionResult<RequestCategory> decisionResult = (DecisionResult<RequestCategory>) result;
        assertThat(decisionResult.content()).isEqualTo(RequestCategory.LEGAL);
        assertThat(decisionResult.tokenUsage()).isEqualTo(new TokenUsage(40, 2));
    }

    @Test
    void should_convert_the_arguments_read_from_the_scope_to_the_declared_types() {
        DecisionModelMock model = answeringYes(0.9);
        CreditEligibility eligibility = AgenticServices.decisionAgentBuilder(CreditEligibility.class)
                .decisionModel(model)
                .build();

        ResultWithAgenticScope<String> result = sequenceOf(eligibility, "eligible")
                .invokeWithAgenticScope(Map.of("customerProfile", "Mario, income 3000 USD", "amount", "1000"));

        assertThat(result.agenticScope().readState("eligible")).isEqualTo(true);
        assertThat(((FieldsInput) model.request().input()).fields()).containsEntry("amount", 1000.0);
    }

    @Test
    void should_fail_when_an_argument_is_missing() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .build();

        assertThatThrownBy(() -> sequenceOf(router, "category").invoke(Map.of()))
                .isInstanceOf(MissingArgumentException.class);
    }

    @Test
    void should_skip_an_optional_agent_when_an_argument_is_missing() {
        DecisionModelMock model = choosing("LEGAL", 0.9);
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(model)
                .optional(true)
                .build();

        ResultWithAgenticScope<String> result = sequenceOf(router, "category").invokeWithAgenticScope(Map.of());

        assertThat(result.agenticScope().hasState("category")).isFalse();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void should_skip_the_agent_when_its_activation_condition_is_false() {
        DecisionModelMock model = choosing("LEGAL", 0.9);
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(model)
                .activationCondition(
                        "the request is not empty",
                        scope -> !scope.readState("request", "").isBlank())
                .build();

        ResultWithAgenticScope<String> result =
                sequenceOf(router, "category").invokeWithAgenticScope(Map.of("request", " "));

        assertThat(result.agenticScope().hasState("category")).isFalse();
        assertThat(model.requests()).isEmpty();
    }

    public static class Category implements TypedKey<RequestCategory> {}

    @Test
    void should_write_the_answer_to_the_output_key_of_the_builder() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .outputKey(Category.class)
                .build();

        ResultWithAgenticScope<String> result =
                sequenceOf(router, "category").invokeWithAgenticScope(Map.of("request", LEGAL_REQUEST));

        assertThat(result.agenticScope().hasState("category")).isFalse();
        assertThat(result.agenticScope().readState(Category.class)).isEqualTo(RequestCategory.LEGAL);
    }

    @Test
    void should_run_asynchronously_and_let_the_next_agent_read_the_answer() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("MEDICAL", 0.9))
                .async(true)
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(
                        router,
                        AgenticServices.agentAction(scope -> scope.writeState(
                                "answer", "The " + scope.readState("category") + " expert will answer")))
                .outputKey("answer")
                .build();

        Object answer = sequence.invoke(Map.of("request", "I have a headache"));

        assertThat(answer).isEqualTo("The MEDICAL expert will answer");
    }

    @Test
    void should_propagate_the_failure_of_the_decision_model() {
        RuntimeException failure = new RuntimeException("rate limit exceeded");
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(new DecisionModelMock(request -> {
                    throw failure;
                }))
                .build();

        assertThatThrownBy(() -> sequenceOf(router, "category").invoke(Map.of("request", LEGAL_REQUEST)))
                .isInstanceOf(AgentInvocationException.class)
                .rootCause()
                .isSameAs(failure);
    }

    @Test
    void should_propagate_a_refusal_as_a_failure_without_writing_the_output_key() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(DecisionModelMock.thatAlwaysAnswers(Map.of("classify", RefusalAnswer.of())))
                .build();
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(router)
                .outputKey("category")
                .listener(monitor)
                .build();

        assertThatThrownBy(() -> sequence.invoke(Map.of("request", LEGAL_REQUEST)))
                .isInstanceOf(AgentInvocationException.class)
                .rootCause()
                .isInstanceOf(ContentFilteredException.class);
        assertThat(monitor.failedExecutions()).hasSize(1);
        assertThat(monitor.failedExecutions().get(0).agenticScope().hasState("category"))
                .isFalse();
    }

    @Test
    void should_retry_the_decision_when_the_error_handler_asks_for_it() {
        AtomicInteger calls = new AtomicInteger();
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(DecisionModelMock.thatAnswers(request -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new RuntimeException("transient failure");
                    }
                    return Map.of(
                            "classify",
                            DecisionMocks.choiceAnswer(
                                    (ChoiceQuestion) request.questions().get("classify"), "LEGAL", 0.9));
                }))
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(router)
                .outputKey("category")
                .errorHandler(context -> ErrorRecoveryResult.retry())
                .build();

        assertThat(sequence.invoke(Map.of("request", LEGAL_REQUEST))).isEqualTo(RequestCategory.LEGAL);
        assertThat(calls).hasValue(2);
    }

    @Test
    void should_write_the_substitute_result_of_the_error_handler() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(DecisionModelMock.thatAlwaysThrowsException())
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(router)
                .outputKey("category")
                .errorHandler(context -> ErrorRecoveryResult.result(RequestCategory.UNKNOWN))
                .build();

        assertThat(sequence.invoke(Map.of("request", LEGAL_REQUEST))).isEqualTo(RequestCategory.UNKNOWN);
    }

    @Test
    void should_report_the_decision_response_to_the_listener_and_the_monitor() {
        List<AgentResponse> responses = new ArrayList<>();
        AgentMonitor monitor = new AgentMonitor();
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9).withTokenUsage(new TokenUsage(40, 2)))
                .listener(new AgentListener() {
                    @Override
                    public void afterAgentInvocation(AgentResponse agentResponse) {
                        responses.add(agentResponse);
                    }
                })
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(router)
                .outputKey("category")
                .listener(monitor)
                .build();

        sequence.invoke(Map.of("request", LEGAL_REQUEST));

        assertThat(responses).hasSize(1);
        AgentResponse response = responses.get(0);
        assertThat(response.output()).isEqualTo(RequestCategory.LEGAL);
        assertThat(response.chatRequest()).isNull();
        assertThat(response.chatResponse()).isNull();
        assertThat(response.decisionResponse().choice("classify").value()).isEqualTo("LEGAL");
        assertThat(response.decisionResponse().choice("classify").probabilityOf("LEGAL"))
                .isCloseTo(0.9, offset(0.001));

        AgentInvocation invocation = monitor.successfulExecutions()
                .get(0)
                .topLevelInvocations()
                .nestedInvocations()
                .get(0);
        assertThat(invocation.agent().topology()).isEqualTo(AgenticSystemTopology.DECISION_AGENT);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(40, 2));
        assertThat(invocation.totalTokenCount()).isEqualTo(42);
        assertThat(invocation.decisionResponse()).isPresent();
        assertThat(invocation.inputs()).containsEntry("request", LEGAL_REQUEST);

        String report = HtmlReportGenerator.generateReport(monitor);
        assertThat(report).contains("Decision").contains("classify = ChoiceAnswer{value=LEGAL");
        // the badge, the card border and the timeline bar of the new topology are styled like the other ones
        assertThat(report)
                .contains("node-border-decision\"")
                .contains("--c-decision: #0d9488;")
                .contains(".topology-badge.decision { background:var(--c-decision); }")
                .contains(".node-border-decision { border-left:3px solid var(--c-decision); }")
                .contains(".bar-decision { background:var(--c-decision); }");
        assertThat(HtmlReportGenerator.generateTopology(sequence)).contains("topology-badge decision");
    }

    @Test
    void should_register_a_monitor_for_a_monitored_agent() {
        MonitoredCategoryRouter router = AgenticServices.decisionAgentBuilder(MonitoredCategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .build();

        sequenceOf(router, "category").invoke(Map.of("request", LEGAL_REQUEST));

        assertThat(router.agentMonitor().successfulExecutions()).hasSize(1);
    }

    @Test
    void should_pick_the_decision_model_from_the_scope_at_every_invocation() {
        DecisionModelMock legalModel = choosing("LEGAL", 0.9);
        DecisionModelMock medicalModel = choosing("MEDICAL", 0.9);
        AtomicInteger resolutions = new AtomicInteger();
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModelProvider(scope -> {
                    resolutions.incrementAndGet();
                    return scope.readState("request", "").contains("headache") ? medicalModel : legalModel;
                })
                .build();
        UntypedAgent sequence = sequenceOf(router, "category");

        assertThat(sequence.invoke(Map.of("request", LEGAL_REQUEST))).isEqualTo(RequestCategory.LEGAL);
        assertThat(sequence.invoke(Map.of("request", "I have a headache"))).isEqualTo(RequestCategory.MEDICAL);
        assertThat(resolutions).hasValue(2);
        assertThat(legalModel.requests()).hasSize(1);
        assertThat(medicalModel.requests()).hasSize(1);
    }

    @Test
    void should_apply_the_request_parameters_of_the_builder() {
        DecisionModelMock model = choosing("LEGAL", 0.9);
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(model)
                .requestParameters(DecisionRequestParameters.builder()
                        .modelName("jev-1.13.0")
                        .build())
                .build();

        sequenceOf(router, "category").invoke(Map.of("request", LEGAL_REQUEST));

        assertThat(model.request().modelName()).isEqualTo("jev-1.13.0");
    }

    @Test
    void should_work_outside_an_agentic_system_and_notify_the_listener() {
        List<AgentResponse> responses = new ArrayList<>();
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .listener(new AgentListener() {
                    @Override
                    public void afterAgentInvocation(AgentResponse agentResponse) {
                        responses.add(agentResponse);
                    }
                })
                .build();

        assertThat(router.classify(LEGAL_REQUEST)).isEqualTo(RequestCategory.LEGAL);
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).inputs()).containsEntry("request", LEGAL_REQUEST);
        assertThat(responses.get(0).decisionResponse()).isNotNull();
    }

    @Test
    void should_wrap_a_failure_outside_an_agentic_system() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("down"))
                .build();

        assertThatThrownBy(() -> router.classify(LEGAL_REQUEST))
                .isInstanceOf(AgentInvocationException.class)
                .rootCause()
                .hasMessage("down");
    }

    @Test
    void should_keep_the_probabilities_of_the_answers_across_the_json_round_trip_of_the_scope() {
        ConfidentCategoryRouter router = AgenticServices.decisionAgentBuilder(ConfidentCategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.7))
                .build();
        StyleScorer scorer = AgenticServices.decisionAgentBuilder(StyleScorer.class)
                .decisionModel(scaling("GOOD", 1.0))
                .build();
        SpamCheck spamCheck = AgenticServices.decisionAgentBuilder(SpamCheck.class)
                .decisionModel(answeringYes(0.42))
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(router, scorer, spamCheck)
                .outputKey("spam")
                .build();

        ResultWithAgenticScope<String> result = sequence.invokeWithAgenticScope(Map.of(
                "request", LEGAL_REQUEST, "story", "Once upon a time", "style", "fairy tale", "message", "Buy now!"));

        AgenticScope scope = result.agenticScope();
        DefaultAgenticScope restored =
                AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson((DefaultAgenticScope) scope));

        assertThat(restored.readState("category")).isEqualTo(scope.readState("category"));
        assertThat(restored.readState("styleScore")).isEqualTo(scope.readState("styleScore"));
        assertThat(restored.readState("spam")).isEqualTo(scope.readState("spam"));
    }

    @Test
    void should_drop_a_record_answer_holding_probabilities_from_the_persisted_scope() {
        ConfidentTicketTriage confidentTriage = AgenticServices.decisionAgentBuilder(ConfidentTicketTriage.class)
                .decisionModel(triaging("BILLING", 0.8, "ANGRY"))
                .build();
        TicketTriage plainTriage = AgenticServices.decisionAgentBuilder(TicketTriage.class)
                .decisionModel(triaging("BILLING", 0.8, "ANGRY"))
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(confidentTriage, plainTriage)
                .outputKey("triage")
                .build();
        AgenticScopeSerializer.allowDeserializationType(Triage.class);

        AgenticScope scope = sequence.invokeWithAgenticScope(Map.of("ticket", "I was charged twice!"))
                .agenticScope();
        ConfidentTriage confident = (ConfidentTriage) scope.readState("confidentTriage");
        assertThat(confident.team().value()).isEqualTo(Team.BILLING);
        assertThat(confident.urgent().probability()).isCloseTo(0.8, offset(0.001));
        assertThat(DefaultAgenticScope.isSerializable(confident)).isFalse();

        DefaultAgenticScope restored =
                AgenticScopeSerializer.fromJson(AgenticScopeSerializer.toJson((DefaultAgenticScope) scope));

        // the record with Choice and YesNoAnswer fields is dropped, the one with enum and boolean fields is kept
        assertThat(restored.readState("confidentTriage")).isNull();
        assertThat(restored.readState("triage")).isEqualTo(scope.readState("triage"));
        assertThat(scope.readState("confidentTriage")).isSameAs(confident);
    }

    @Test
    void should_leave_a_decision_result_out_of_the_json_of_the_scope() {
        AuditedCategoryRouter router = AgenticServices.decisionAgentBuilder(AuditedCategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .build();

        ResultWithAgenticScope<String> result =
                sequenceOf(router, "category").invokeWithAgenticScope(Map.of("request", LEGAL_REQUEST));

        DefaultAgenticScope restored = AgenticScopeSerializer.fromJson(
                AgenticScopeSerializer.toJson((DefaultAgenticScope) result.agenticScope()));

        assertThat(result.agenticScope().readState("category")).isInstanceOf(DecisionResult.class);
        assertThat(restored.hasState("category")).isFalse();
        assertThat(restored.readState("request")).isEqualTo(LEGAL_REQUEST);
    }

    @Test
    void should_compose_its_listener_with_the_inherited_one_of_its_parent() {
        List<String> events = new ArrayList<>();
        AgentListener inherited = new AgentListener() {
            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                events.add("parent:" + agentResponse.agentName());
            }

            @Override
            public boolean inheritedBySubagents() {
                return true;
            }
        };
        SpamCheck spamCheck = AgenticServices.decisionAgentBuilder(SpamCheck.class)
                .decisionModel(answeringYes(0.9))
                .listener(new AgentListener() {
                    @Override
                    public void afterAgentInvocation(AgentResponse agentResponse) {
                        events.add("own:" + agentResponse.agentName());
                    }
                })
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(spamCheck)
                .outputKey("spam")
                .listener(inherited)
                .build();

        sequence.invoke(Map.of("message", "Buy now!"));

        assertThat(events).contains("own:isSpam", "parent:isSpam");
    }

    @Test
    void should_expose_the_decision_response_once_per_invocation() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .build();
        UntypedAgent sequence = sequenceOf(router, "category");
        ResultWithAgenticScope<String> result = sequence.invokeWithAgenticScope(Map.of("request", LEGAL_REQUEST));

        DecisionResponse afterInvocation = ((DecisionResponseAccess) router)
                .lastDecisionResponse(result.agenticScope().memoryId());

        // the scope forgets the response once it has registered the invocation
        assertThat(afterInvocation).isNull();
    }

    @Test
    void should_not_keep_the_decision_response_of_an_asynchronous_invocation() {
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .async(true)
                .build();
        UntypedAgent sequence = sequenceOf(router, "category");

        ResultWithAgenticScope<String> result = sequence.invokeWithAgenticScope(Map.of("request", LEGAL_REQUEST));

        assertThat(result.agenticScope().readState("category")).isEqualTo(RequestCategory.LEGAL);
        assertThat(((DecisionResponseAccess) router)
                        .lastDecisionResponse(result.agenticScope().memoryId()))
                .isNull();
    }

    @Test
    void should_reject_the_chat_interfaces_of_a_wrapper_made_for_an_ai_agent() {
        // the class an integration generates for an AI agent forwards the chat interfaces to the handler
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(choosing("LEGAL", 0.9))
                .agentInstanceFactory(handler -> Proxy.newProxyInstance(
                        CategoryRouter.class.getClassLoader(),
                        new Class<?>[] {
                            CategoryRouter.class, InternalAgent.class, ChatMessagesAccess.class, ChatMemoryAccess.class
                        },
                        (InvocationHandler) handler))
                .build();

        assertThatThrownBy(() -> ((ChatMessagesAccess) router).lastChatRequest("scope"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("has no chat memory or chat messages")
                .hasMessageContaining("ChatMessagesAccess")
                .hasMessageContaining("must implement DecisionResponseAccess");
        assertThatThrownBy(() -> ((ChatMemoryAccess) router).getChatMemory("scope"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ChatMemoryAccess");
    }
}
