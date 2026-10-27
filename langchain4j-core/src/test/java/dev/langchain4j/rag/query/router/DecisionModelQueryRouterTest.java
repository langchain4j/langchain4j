package dev.langchain4j.rag.query.router;

import static dev.langchain4j.rag.query.router.DecisionModelQueryRouter.FallbackStrategy.FAIL;
import static dev.langchain4j.rag.query.router.DecisionModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelQueryRouterTest {

    final ContentRetriever hr = mock(ContentRetriever.class);
    final ContentRetriever wiki = mock(ContentRetriever.class);
    final Map<ContentRetriever, String> retrievers = new LinkedHashMap<>();

    {
        retrievers.put(hr, "HR policies");
        retrievers.put(wiki, "Engineering wiki");
    }

    DecisionModelMock decisionModel;

    DecisionModelMock answering(double hrProbability, double wikiProbability) {
        decisionModel = DecisionModelMock.thatAlwaysAnswers(Map.of(
                "source1", YesNoAnswer.from(hrProbability),
                "source2", YesNoAnswer.from(wikiProbability)));
        return decisionModel;
    }

    final DecisionModel failing = DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("decision model is down");

    @Test
    void should_route_to_retrievers_above_threshold() {

        QueryRouter router = new DecisionModelQueryRouter(answering(0.9, 0.2), retrievers);

        assertThat(router.route(Query.from("How many vacation days do I have?"))).containsExactly(hr);
        assertThat(decisionModel.request().input()).isEqualTo(FieldsInput.from(Map.of("query", "How many vacation days do I have?")));
        assertThat(decisionModel.request().questions())
                .containsEntry(
                        "source1",
                        YesNoQuestion.from(
                                "Could the following data source contain information that helps answer the query?\n"
                                        + "HR policies"));
    }

    @Test
    void should_send_system_message_of_the_query_also_in_the_first_turn_and_with_one_message() {

        Metadata metadata = new Metadata(
                UserMessage.from("What are the opening hours of the canteen?"),
                SystemMessage.from("You are the assistant of the Berlin office."),
                "memory-id",
                List.of());

        new DecisionModelQueryRouter(answering(0.9, 0.2), retrievers)
                .route(Query.from("What are the opening hours of the canteen?", metadata));
        DecisionModelQueryRouter.builder()
                .decisionModel(decisionModel)
                .retrieverToDescription(retrievers)
                .maxMessages(1)
                .build()
                .route(Query.from("What are the opening hours of the canteen?", metadata));

        FieldsInput expected = FieldsInput.from(Map.of(
                "query", "What are the opening hours of the canteen?",
                "messages", List.of(Map.of("role", "system", "text", "You are the assistant of the Berlin office."))));
        assertThat(decisionModel.requests()).extracting(DecisionRequest::input).containsExactly(expected, expected);
    }

    @Test
    void should_send_the_system_message_once_with_tool_messages_and_validate_limits() {

        ToolExecutionRequest getEmployee = ToolExecutionRequest.builder()
                .id("1")
                .name("getEmployee")
                .arguments("{}")
                .build();
        Metadata metadata = new Metadata(
                UserMessage.from("How many vacation days do I get?"),
                SystemMessage.from("You are the HR assistant."),
                "memory-id",
                List.of(
                        SystemMessage.from("You are the HR assistant."),
                        UserMessage.from("Who am I?"),
                        AiMessage.from(getEmployee),
                        ToolExecutionResultMessage.from(getEmployee, "{\"country\":\"Germany\"}"),
                        AiMessage.from("You are Jan."),
                        UserMessage.from("How many vacation days do I get?")));

        DecisionModelQueryRouter.builder()
                .decisionModel(answering(0.9, 0.2))
                .retrieverToDescription(retrievers)
                .maxMessages(4)
                .build()
                .route(Query.from("How many vacation days do I get?", metadata));

        assertThat(decisionModel.request().input())
                .isEqualTo(FieldsInput.from(Map.of(
                        "query", "How many vacation days do I get?",
                        "messages",
                        List.of(
                                Map.of("role", "system", "text", "You are the HR assistant."),
                                Map.of("role", "user", "text", "Who am I?"),
                                Map.of(
                                        "role",
                                        "assistant",
                                        "tool_calls",
                                        List.of(Map.of("id", "1", "name", "getEmployee", "arguments", "{}"))),
                                Map.of("role", "tool", "tool_call_id", "1", "name", "getEmployee",
                                        "text", "{\"country\":\"Germany\"}"),
                                Map.of("role", "assistant", "text", "You are Jan.")))));
        assertThatThrownBy(() -> DecisionModelQueryRouter.builder()
                        .decisionModel(decisionModel)
                        .retrieverToDescription(retrievers)
                        .maxMessageLength(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageLength");
    }

    @Test
    void should_send_previous_messages_of_the_conversation_with_the_query() {

        QueryRouter router = new DecisionModelQueryRouter(answering(0.9, 0.2), retrievers);
        Metadata metadata = new Metadata(
                UserMessage.from("And for contractors?"),
                "memory-id",
                List.of(
                        UserMessage.from("Hi!"),
                        AiMessage.from("Hello! How can I help?"),
                        UserMessage.from("How many vacation days do employees get?"),
                        AiMessage.from("Employees get 28 days per year."),
                        UserMessage.from("And for contractors?")));

        router.route(Query.from("And for contractors?", metadata));

        assertThat(decisionModel.request().input())
                .isEqualTo(FieldsInput.from(Map.of(
                        "query", "And for contractors?",
                        "messages",
                        List.of(
                                Map.of("role", "user", "text", "Hi!"),
                                Map.of("role", "assistant", "text", "Hello! How can I help?"),
                                Map.of("role", "user", "text", "How many vacation days do employees get?"),
                                Map.of("role", "assistant", "text", "Employees get 28 days per year.")))));
    }

    @Test
    void should_send_the_query_and_the_9_previous_messages_by_default() {

        QueryRouter router = new DecisionModelQueryRouter(answering(0.9, 0.2), retrievers);
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            messages.add(i % 2 == 0 ? UserMessage.from("question " + i) : AiMessage.from("answer " + i));
        }
        Metadata metadata = new Metadata(UserMessage.from("question 12"), "memory-id", messages);

        router.route(Query.from("question 12", metadata));

        List<Map<String, Object>> expected = new ArrayList<>();
        for (int i = 3; i <= 11; i++) {
            expected.add(i % 2 == 0
                    ? Map.of("role", "user", "text", "question " + i)
                    : Map.of("role", "assistant", "text", "answer " + i));
        }
        assertThat(decisionModel.request().input())
                .isEqualTo(FieldsInput.from(Map.of("query", "question 12", "messages", expected)));
    }

    @Test
    void should_send_only_the_query_with_max_messages_of_1() {

        QueryRouter router = DecisionModelQueryRouter.builder()
                .decisionModel(answering(0.9, 0.2))
                .retrieverToDescription(retrievers)
                .maxMessages(1)
                .build();
        Metadata metadata = new Metadata(
                UserMessage.from("And for contractors?"),
                "memory-id",
                List.of(UserMessage.from("How many vacation days do employees get?"), AiMessage.from("28 days.")));

        router.route(Query.from("And for contractors?", metadata));

        assertThat(decisionModel.request().input()).isEqualTo(FieldsInput.from(Map.of("query", "And for contractors?")));
    }

    @Test
    void should_route_to_several_retrievers() {

        QueryRouter router = DecisionModelQueryRouter.builder()
                .decisionModel(answering(0.6, 0.7))
                .retrieverToDescription(retrievers)
                .minProbability(0.55)
                .build();

        assertThat(router.route(Query.from("Who is on call for the payroll service?")))
                .containsExactly(hr, wiki);
    }

    @Test
    void should_not_route_when_no_retriever_helps() {

        QueryRouter router = new DecisionModelQueryRouter(answering(0.1, 0.1), retrievers);

        assertThat(router.route(Query.from("Hi!"))).isEmpty();
    }

    @Test
    void should_apply_fallback_strategy_when_decision_model_fails() {

        assertThat(new DecisionModelQueryRouter(failing, retrievers).route(Query.from("query")))
                .isEmpty();
        assertThat(DecisionModelQueryRouter.builder()
                        .decisionModel(failing)
                        .retrieverToDescription(retrievers)
                        .fallbackStrategy(ROUTE_TO_ALL)
                        .build()
                        .route(Query.from("query")))
                .containsExactly(hr, wiki);
        assertThatThrownBy(() -> DecisionModelQueryRouter.builder()
                        .decisionModel(failing)
                        .retrieverToDescription(retrievers)
                        .fallbackStrategy(FAIL)
                        .build()
                        .route(Query.from("query")))
                .hasMessage("decision model is down");
    }

    @Test
    void should_apply_fallback_strategy_when_decision_model_refuses_to_answer() {

        DecisionModel refusing = DecisionModelMock.thatAlwaysAnswers(
                Map.of("source1", RefusalAnswer.of(), "source2", YesNoAnswer.from(0.9)));
        QueryRouter router = DecisionModelQueryRouter.builder()
                .decisionModel(refusing)
                .retrieverToDescription(retrievers)
                .fallbackStrategy(ROUTE_TO_ALL)
                .build();

        assertThat(router.route(Query.from("query"))).containsExactly(hr, wiki);
        assertThat(router.routeAsync(Query.from("query")).join()).containsExactly(hr, wiki);
    }

    @Test
    void should_route_asynchronously() {

        QueryRouter router = new DecisionModelQueryRouter(answering(0.2, 0.9), retrievers);

        assertThat(router.routeAsync(Query.from("How do I deploy?")).join()).containsExactly(wiki);
        assertThat(new DecisionModelQueryRouter(failing, retrievers)
                        .routeAsync(Query.from("query"))
                        .join())
                .isEmpty();
    }

    @Test
    void should_fail_asynchronously_with_fail_strategy() {

        QueryRouter router = DecisionModelQueryRouter.builder()
                .decisionModel(failing)
                .retrieverToDescription(retrievers)
                .fallbackStrategy(FAIL)
                .build();

        assertThat(router.routeAsync(Query.from("query")))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .withMessage("decision model is down");
    }

    @Test
    void should_propagate_missing_async_support_so_that_retrieval_augmentor_can_offload_routing() {

        QueryRouter router = new DecisionModelQueryRouter(answering(0.9, 0.1).withoutAsyncSupport(), retrievers);

        assertThat(router.routeAsync(Query.from("query")))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(AsyncNotSupportedException.class);
        assertThat(router.route(Query.from("query"))).containsExactly(hr);
    }

    @Test
    void should_use_custom_question_template() {

        DecisionModelQueryRouter.builder()
                .decisionModel(answering(0.9, 0.1))
                .retrieverToDescription(retrievers)
                .questionTemplate(PromptTemplate.from("Can this source answer the question? Source: {{description}}"))
                .build()
                .route(Query.from("query"));

        assertThat(decisionModel.request().questions())
                .containsEntry("source1", YesNoQuestion.from("Can this source answer the question? Source: HR policies"));
    }

    @Test
    void should_require_description_in_question_template() {

        assertThatThrownBy(() -> DecisionModelQueryRouter.builder()
                        .decisionModel(answering(0.9, 0.1))
                        .retrieverToDescription(retrievers)
                        .questionTemplate(PromptTemplate.from("Can this source help?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{description}}");
    }

    @Test
    void should_reject_question_template_with_other_variables() {

        assertThatThrownBy(() -> DecisionModelQueryRouter.builder()
                        .decisionModel(answering(0.9, 0.2))
                        .retrieverToDescription(retrievers)
                        .questionTemplate(PromptTemplate.from("Does {{description}} help with {{query}}?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can only use the {{description}} variable");
    }

    @Test
    void should_validate_configuration() {

        assertThatThrownBy(() -> DecisionModelQueryRouter.builder()
                        .decisionModel(answering(0, 0))
                        .retrieverToDescription(retrievers)
                        .minProbability(1.5)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minProbability");
        assertThatThrownBy(() -> new DecisionModelQueryRouter(null, retrievers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");

        assertThatThrownBy(() -> new DecisionModelQueryRouter(answering(0, 0), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retrieverToDescription");
        assertThatThrownBy(() -> new DecisionModelQueryRouter(answering(0, 0), Map.of(hr, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }
}
