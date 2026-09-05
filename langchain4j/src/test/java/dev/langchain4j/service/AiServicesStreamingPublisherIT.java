package dev.langchain4j.service;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServiceStreamingEvent.AfterToolExecutionEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * End-to-end integration tests for the non-blocking streaming AI Service ({@link Flow.Publisher} return types)
 * against a real provider that implements the reactive
 * {@link StreamingChatModel#doChat(dev.langchain4j.model.chat.request.ChatRequest)} publisher (OpenAI).
 * <p>
 * The unit tests ({@code AiServiceStreamingPublisherTest}) drive the tool loop, error handling, multiple/async
 * tools, tool providers and cancellation against a mock; these tests validate that the whole stack works over a
 * real provider's reactive stream.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiServicesStreamingPublisherIT {

    static StreamingChatModel model() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .build();
    }

    interface StringStreamer {
        Flow.Publisher<String> chat(String message);
    }

    interface EventStreamer {
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    static class WeatherService {

        @Tool("returns the weather forecast for a given city")
        String getWeather(String city) {
            return "It is 42 degrees and sunny in " + city;
        }
    }

    @Test
    void streams_an_answer_without_tools() throws Exception {
        StringStreamer assistant =
                AiServices.builder(StringStreamer.class).streamingChatModel(model()).build();

        List<String> tokens = collect(assistant.chat("What is the capital of Germany? Answer with a single word."));

        assertThat(String.join("", tokens)).containsIgnoringCase("Berlin");
    }

    @Test
    void streams_events_with_a_tool() throws Exception {
        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new WeatherService())
                .build();

        List<AiServiceStreamingEvent> events = collect(assistant.chat("What is the weather in Munich?"));

        // the tool was executed
        assertThat(events)
                .filteredOn(e -> e instanceof AfterToolExecutionEvent)
                .isNotEmpty()
                .allSatisfy(e -> assertThat(((AfterToolExecutionEvent) e).toolExecution().result())
                        .contains("42"));

        // exactly one final response, emitted last, carrying the answer
        assertThat(events)
                .filteredOn(e -> e instanceof FinalResponseEvent)
                .singleElement()
                .satisfies(e -> assertThat(((FinalResponseEvent) e).chatResponse().aiMessage().text())
                        .contains("42"));
        assertThat(events.get(events.size() - 1)).isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void uses_tool_provider() throws Exception {
        ToolSpecification specification = ToolSpecification.builder()
                .name("getWeather")
                .description("returns the weather forecast for a given city")
                .parameters(JsonObjectSchema.builder().addStringProperty("city").required("city").build())
                .build();

        // a tool provider supplying an async-capable executor (required by the reactive path)
        ToolExecutor executor = new ToolExecutor() {
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "It is 42 degrees and sunny";
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(
                    ToolExecutionRequest request, InvocationContext context) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.builder().resultText("It is 42 degrees and sunny").build());
            }
        };
        ToolProvider toolProvider =
                request -> ToolProviderResult.builder().add(specification, executor).build();

        EventStreamer assistant = AiServices.builder(EventStreamer.class)
                .streamingChatModel(model())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .toolProvider(toolProvider)
                .build();

        List<AiServiceStreamingEvent> events = collect(assistant.chat("What is the weather in Munich?"));

        assertThat(events).filteredOn(e -> e instanceof AfterToolExecutionEvent).isNotEmpty();
        assertThat(events)
                .filteredOn(e -> e instanceof FinalResponseEvent)
                .singleElement()
                .satisfies(e -> assertThat(((FinalResponseEvent) e).chatResponse().aiMessage().text())
                        .contains("42"));
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws InterruptedException {
        List<T> items = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                items.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        assertThat(latch.await(60, SECONDS)).as("stream completed within 60s").isTrue();
        assertThat(error.get()).isNull();
        return items;
    }
}
