package dev.langchain4j.agentic;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.decision.DecisionAgents.CategoryRouter;
import dev.langchain4j.agentic.decision.DecisionAgents.RequestCategory;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.AgentMessage;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Concurrent invocations of the same agent in one agentic scope, as made by a parallel mapper or by an asynchronous
 * agent invoked again while still running, report each their own model response to the listeners and to the
 * context of the scope.
 */
class ConcurrentAgentResponsesTest {

    private static final List<String> QUESTIONS =
            IntStream.rangeClosed(1, 8).mapToObj(i -> "question " + i).toList();

    public interface Assistant {
        @UserMessage("Answer {{question}}")
        @Agent(outputKey = "answer")
        String answer(@V("question") String question);
    }

    public interface StreamingAssistant {
        @UserMessage("Answer {{question}}")
        @Agent(outputKey = "answer")
        TokenStream answer(@V("question") String question);
    }

    private static final List<TokenUsage> TOKEN_USAGES =
            IntStream.rangeClosed(1, QUESTIONS.size()).mapToObj(i -> new TokenUsage(10 * i, i)).toList();

    /** Waits until all the model calls of the items are in flight, so that the invocations really overlap. */
    private static ChatModelMock modelAnsweringAllAtOnce(int parties) {
        CyclicBarrier allCallsInFlight = new CyclicBarrier(parties);
        return ChatModelMock.thatResponds(chatRequest -> {
                    String question = ((dev.langchain4j.data.message.UserMessage) chatRequest
                                    .messages()
                                    .get(chatRequest.messages().size() - 1))
                            .singleText()
                            .substring("Answer ".length());
                    await(allCallsInFlight);
                    return AiMessage.from("Answer to " + question);
                })
                .withTokenUsages(TOKEN_USAGES.subList(0, parties).toArray(TokenUsage[]::new));
    }

    private static DecisionModelMock modelDecidingAllAtOnce(int parties) {
        CyclicBarrier allCallsInFlight = new CyclicBarrier(parties);
        return new DecisionModelMock(request -> {
            String question =
                    ((FieldsInput) request.input()).fields().get("request").toString();
            await(allCallsInFlight);
            double probability = probabilityOf(question);
            Map.Entry<String, ChoiceQuestion> entry = request.questions().entrySet().stream()
                    .filter(e -> e.getValue() instanceof ChoiceQuestion)
                    .map(e -> Map.entry(e.getKey(), (ChoiceQuestion) e.getValue()))
                    .findFirst()
                    .orElseThrow();
            List<String> options = List.copyOf(entry.getValue().options().keySet());
            Map<String, Double> probabilities = new LinkedHashMap<>();
            options.forEach(option -> probabilities.put(
                    option, option.equals("LEGAL") ? probability : (1 - probability) / (options.size() - 1)));
            return Map.of(
                    entry.getKey(),
                    ChoiceAnswer.builder()
                            .value("LEGAL")
                            .probabilities(probabilities)
                            .options(options)
                            .build());
        });
    }

    /** Numbers its answers, so that two overlapping invocations with the same input get different responses. */
    private static ChatModelMock modelNumberingItsAnswers(int parties) {
        CyclicBarrier allCallsInFlight = new CyclicBarrier(parties);
        AtomicInteger calls = new AtomicInteger();
        return ChatModelMock.thatResponds(chatRequest -> {
                    int call = calls.incrementAndGet();
                    await(allCallsInFlight);
                    return AiMessage.from("Answer #" + call);
                })
                .withTokenUsages(TOKEN_USAGES.subList(0, parties).toArray(TokenUsage[]::new));
    }

    private static double probabilityOf(String question) {
        int index = Integer.parseInt(question.substring("question ".length()));
        return 0.5 + index / 20.0;
    }

    /** The item of a parallel mapper invocation, from the index the mapper appends to the id of the subagent. */
    private static String itemOf(AgentResponse response) {
        String agentId = response.agentId();
        return QUESTIONS.get(Integer.parseInt(agentId.substring(agentId.lastIndexOf('_') + 1)));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the model calls of the items", e);
        } catch (Exception e) {
            throw new IllegalStateException("The model calls of the items did not overlap", e);
        }
    }

    private static AgentListener collecting(List<AgentResponse> responses) {
        return collecting(responses, null);
    }

    private static AgentListener collecting(List<AgentResponse> responses, CountDownLatch allReported) {
        return new AgentListener() {
            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                responses.add(agentResponse);
                if (allReported != null) {
                    allReported.countDown();
                }
            }
        };
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(QUESTIONS.size());

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void items_of_a_parallel_mapper_report_each_their_own_chat_request_and_response() {
        List<AgentResponse> responses = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelAnsweringAllAtOnce(QUESTIONS.size()))
                .listener(collecting(responses))
                .build();
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(assistant)
                .itemsProvider("questions")
                .outputKey("answers")
                .executor(executor)
                .build();

        mapper.invoke(Map.of("questions", QUESTIONS));

        assertThat(responses).hasSize(QUESTIONS.size());
        for (AgentResponse response : responses) {
            String question = itemOf(response);
            assertThat(response.chatRequest().messages())
                    .last()
                    .isEqualTo(dev.langchain4j.data.message.UserMessage.from("Answer " + question));
            assertThat(response.chatResponse().aiMessage().text()).isEqualTo("Answer to " + question);
            assertThat(response.output()).isEqualTo("Answer to " + question);
        }
        assertThat(responses)
                .extracting(ConcurrentAgentResponsesTest::itemOf)
                .containsExactlyInAnyOrderElementsOf(QUESTIONS);
        assertThat(responses)
                .extracting(response -> response.chatResponse().tokenUsage())
                .containsExactlyInAnyOrderElementsOf(TOKEN_USAGES);
    }

    @Test
    void items_of_a_parallel_mapper_register_each_their_own_messages_in_the_context_of_the_scope() {
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelAnsweringAllAtOnce(QUESTIONS.size()))
                .build();
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(assistant)
                .itemsProvider("questions")
                .outputKey("answers")
                .executor(executor)
                .build();

        ResultWithAgenticScope<String> result = mapper.invokeWithAgenticScope(Map.of("questions", QUESTIONS));

        List<AgentMessage> context = ((DefaultAgenticScope) result.agenticScope()).context();
        assertThat(context).hasSize(2 * QUESTIONS.size());
        for (int i = 0; i < context.size(); i += 2) {
            AgentMessage userMessage = context.get(i);
            AgentMessage aiMessage = context.get(i + 1);
            String question = ((dev.langchain4j.data.message.UserMessage) userMessage.message())
                    .singleText()
                    .substring("Answer ".length());
            assertThat(aiMessage.agentId()).isEqualTo(userMessage.agentId());
            assertThat(aiMessage.message()).isEqualTo(AiMessage.from("Answer to " + question));
        }
        assertThat(context)
                .filteredOn(message -> message.message() instanceof AiMessage)
                .extracting(AgentMessage::agentId)
                .doesNotHaveDuplicates();
    }

    @Test
    void items_of_a_parallel_mapper_report_each_their_own_decision_response() {
        List<AgentResponse> responses = new CopyOnWriteArrayList<>();
        CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
                .decisionModel(modelDecidingAllAtOnce(QUESTIONS.size()))
                .listener(collecting(responses))
                .build();
        UntypedAgent mapper = AgenticServices.parallelMapperBuilder()
                .subAgents(router)
                .itemsProvider("requests")
                .outputKey("categories")
                .executor(executor)
                .build();

        mapper.invoke(Map.of("requests", QUESTIONS));

        assertThat(responses).hasSize(QUESTIONS.size());
        for (AgentResponse response : responses) {
            String question = itemOf(response);
            assertThat(response.decisionResponse().choice("classify").probabilityOf("LEGAL"))
                    .isEqualTo(probabilityOf(question));
            assertThat(response.output()).isEqualTo(RequestCategory.LEGAL);
            assertThat(response.chatRequest()).isNull();
            assertThat(response.chatResponse()).isNull();
        }
    }

    @Test
    void asynchronous_agent_invoked_again_while_running_reports_each_invocation_its_own_response() throws Exception {
        List<AgentResponse> responses = new CopyOnWriteArrayList<>();
        CountDownLatch bothReported = new CountDownLatch(2);
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelNumberingItsAnswers(2))
                .async(true)
                .listener(collecting(responses, bothReported))
                .build();
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(assistant)
                .maxIterations(2)
                .exitCondition(scope -> false)
                .build();

        loop.invoke(Map.of("question", "question 1"));

        assertThat(bothReported.await(10, SECONDS)).isTrue();
        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(response -> response.chatResponse().aiMessage().text())
                .containsExactlyInAnyOrder("Answer #1", "Answer #2");
        assertThat(responses)
                .extracting(response -> response.chatResponse().tokenUsage())
                .containsExactlyInAnyOrder(TOKEN_USAGES.get(0), TOKEN_USAGES.get(1));
        for (AgentResponse response : responses) {
            assertThat(response.chatRequest().messages())
                    .last()
                    .isEqualTo(dev.langchain4j.data.message.UserMessage.from("Answer question 1"));
        }
    }

    @Test
    void streaming_agent_reports_no_chat_response_rather_than_the_one_of_its_previous_invocation() {
        List<AgentResponse> responses = new CopyOnWriteArrayList<>();
        StreamingAssistant assistant = AgenticServices.agentBuilder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("It ", "is ", "noon"))
                .listener(collecting(responses))
                .build();
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(assistant)
                .maxIterations(2)
                .exitCondition(scope -> false)
                .build();

        loop.invoke(Map.of("question", "question 1"));

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(AgentResponse::chatRequest).containsOnlyNulls();
        assertThat(responses).extracting(AgentResponse::chatResponse).containsOnlyNulls();
    }
}
