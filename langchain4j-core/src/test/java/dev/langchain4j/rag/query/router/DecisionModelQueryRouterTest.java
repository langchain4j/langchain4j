package dev.langchain4j.rag.query.router;

import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.FAIL;
import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.time.Duration;
import java.util.LinkedHashMap;
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
                "source1", YesNoAnswer.of(hrProbability),
                "source2", YesNoAnswer.of(wikiProbability)));
        return decisionModel;
    }

    final DecisionModel failing = DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("decision model is down");

    @Test
    void should_route_to_retrievers_above_threshold() {

        QueryRouter router = new DecisionModelQueryRouter(answering(0.9, 0.2), retrievers);

        assertThat(router.route(Query.from("How many vacation days do I have?"))).containsExactly(hr);
        assertThat(decisionModel.request().input()).isEqualTo("How many vacation days do I have?");
        assertThat(decisionModel.request().questions())
                .containsEntry(
                        "source1",
                        YesNoQuestion.of(
                                "Could the following data source contain information that helps answer the query?\n"
                                        + "HR policies"));
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
    void should_validate_configuration() {

        assertThatThrownBy(() -> new DecisionModelQueryRouter(answering(0, 0), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retrieverToDescription");
        assertThatThrownBy(() -> new DecisionModelQueryRouter(answering(0, 0), Map.of(hr, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }
}
