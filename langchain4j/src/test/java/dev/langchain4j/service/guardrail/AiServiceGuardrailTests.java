package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailException;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AiServiceGuardrailTests {
    private static final ImageContent IMAGE_CONTENT = ImageContent.from(
            Image.builder().url("https://example.com/image.png").build());

    @Test
    void noGuardrails() {
        var noGuardrails = Assistant.create();

        assertThat(noGuardrails.chat("Hello!")).isEqualTo("Request: Hello!; Response: Hi!");
        assertThat(noGuardrails.chat2("Hello!")).isEqualTo("Request: Hello!; Response: Hi!");
    }

    @ParameterizedTest
    @MethodSource("classLevelAssistants")
    void classLevelAssistants(String testDescription, Assistant assistant) {
        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat("Hello!"))
                .withMessageContaining(
                        "The guardrail %s failed with this message: Request: Hello! from %s; Response: Hi! failure from %s",
                        OutputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        OutputGuardrailFail.class.getSimpleName());
        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat2("Hello!"))
                .withMessageContaining(
                        "The guardrail %s failed with this message: Request: Hello! from %s; Response: Hi! failure from %s",
                        OutputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        OutputGuardrailFail.class.getSimpleName());
    }

    @Test
    void methodLevelAssistant() {
        var assistant = MethodLevelAssistant.create();

        assertThat(assistant.chat("Hello!"))
                .isEqualTo(
                        "Request: Hello! from %s; Response: Hi! from %s",
                        InputGuardrailSuccess.class.getSimpleName(), OutputGuardrailSuccess.class.getSimpleName());
        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat2("Hello!"))
                .withMessage(
                        "The guardrail %s failed with this message: Hello! failure from %s",
                        InputGuardrailFail.class.getName(), InputGuardrailFail.class.getSimpleName());
    }

    @Test
    void anotherMethodLevelAssistant() {
        var assistant = MethodLevelAssistant1.create();

        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat("Hello!"))
                .withMessage(
                        "The guardrail %s failed with this message: Hello! from %s failure from %s",
                        InputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        InputGuardrailFail.class.getSimpleName());

        assertThat(assistant.chat2("Hello!")).isEqualTo("Request: Hello!; Response: Hi!");
    }

    @Test
    void classAndMethodLevelAssistant() {
        var assistant = ClassAndMethodLevelAssistant.create();

        assertThatExceptionOfType(GuardrailException.class)
                .isThrownBy(() -> assistant.chat("Hello!"))
                .withMessage(
                        "The guardrail %s failed with this message: Hello! from %s failure from %s",
                        InputGuardrailFail.class.getName(),
                        InputGuardrailSuccess.class.getSimpleName(),
                        InputGuardrailFail.class.getSimpleName());

        assertThat(assistant.chat2("Hello!"))
                .isEqualTo(
                        "Request: Hello! from %s; Response: Hi! from %s",
                        InputGuardrailSuccess.class.getSimpleName(), OutputGuardrailSuccess.class.getSimpleName());
    }

    @Test
    void input_guardrail_should_receive_materialized_multimodal_user_message() {
        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds("does not matter");
        RecordingInputGuardrail inputGuardrail = new RecordingInputGuardrail();
        VisionAssistant assistant = AiServices.builder(VisionAssistant.class)
                .chatModel(chatModelMock)
                .inputGuardrails(inputGuardrail)
                .build();

        assistant.describe(IMAGE_CONTENT);

        assertThat(inputGuardrail.observedUserMessage()).isNotNull();
        assertThat(inputGuardrail.observedUserMessage().contents())
                .containsExactly(TextContent.from("Describe this image"), IMAGE_CONTENT);
        assertThat(inputGuardrail.observedUserMessage().hasSingleText()).isFalse();
        assertThat(chatModelMock.request().messages().get(0)).isEqualTo(inputGuardrail.observedUserMessage());
    }

    @Test
    void input_guardrail_should_observe_augmented_user_message_after_rag_and_before_chat_request() {
        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds("does not matter");
        RecordingInputGuardrail inputGuardrail = new RecordingInputGuardrail();
        AtomicReference<UserMessage> userMessageSeenByAugmentor = new AtomicReference<>();
        RetrievalAugmentor retrievalAugmentor = (AugmentationRequest request) -> {
            userMessageSeenByAugmentor.set((UserMessage) request.chatMessage());
            return new AugmentationResult(UserMessage.from("Augmented prompt"), null);
        };

        VisionAssistant assistant = AiServices.builder(VisionAssistant.class)
                .chatModel(chatModelMock)
                .inputGuardrails(inputGuardrail)
                .retrievalAugmentor(retrievalAugmentor)
                .build();

        assistant.describe(IMAGE_CONTENT);

        assertThat(userMessageSeenByAugmentor.get().contents())
                .containsExactly(TextContent.from("Describe this image"));
        assertThat(inputGuardrail.observedUserMessage().contents())
                .containsExactly(TextContent.from("Augmented prompt"), IMAGE_CONTENT);
        assertThat(chatModelMock.request().messages().get(0)).isEqualTo(inputGuardrail.observedUserMessage());
    }

    @Test
    void input_guardrail_rewrite_should_still_work_for_plain_text_requests() {
        ChatModelMock chatModelMock = ChatModelMock.thatAlwaysResponds("does not matter");
        PlainTextAssistant assistant = AiServices.builder(PlainTextAssistant.class)
                .chatModel(chatModelMock)
                .inputGuardrails(new RewritingInputGuardrail())
                .build();

        assistant.chat("Original prompt");

        UserMessage userMessage =
                (UserMessage) chatModelMock.request().messages().get(0);
        assertThat(userMessage.contents()).containsExactly(TextContent.from("Rewritten prompt"));
        assertThat(userMessage.hasSingleText()).isTrue();
    }

    static Stream<Arguments> classLevelAssistants() {
        return Stream.of(
                Arguments.of("assistant with class-level annotations", ClassLevelAssistant.create()),
                Arguments.of("assistant with method-level annotations", SameMethodLevelAssistant.create()),
                Arguments.of(
                        "assistant with guardrail classes defined (class-level)",
                        ClassLevelAssistant.createUsingClassNames()),
                Arguments.of(
                        "assistant with guardrail instances defined (class-level)",
                        ClassLevelAssistant.createUsingClassInstances()),
                Arguments.of(
                        "assistant with guardrail classes defined (method-level)",
                        SameMethodLevelAssistant.createUsingClassNames()),
                Arguments.of(
                        "assistant with guardrail instances defined (method-level)",
                        SameMethodLevelAssistant.createUsingClassInstances()));
    }

    interface Assistant {
        String chat(String message);

        String chat2(String message);

        static <T extends Assistant> T create(Class<T> clazz) {
            return AiServices.create(clazz, new MyChatModel());
        }

        static Assistant create() {
            return create(Assistant.class);
        }
    }

    interface VisionAssistant {
        @dev.langchain4j.service.UserMessage("Describe this image")
        String describe(@dev.langchain4j.service.UserMessage ImageContent image);
    }

    interface PlainTextAssistant {
        String chat(String message);
    }

    @InputGuardrails(InputGuardrailSuccess.class)
    @OutputGuardrails(OutputGuardrailFail.class)
    interface ClassLevelAssistant extends Assistant {
        static Assistant create() {
            return AiServices.create(ClassLevelAssistant.class, new MyChatModel());
        }

        static Assistant createUsingClassNames() {
            return AiServices.builder(ClassLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrailClasses(InputGuardrailSuccess.class)
                    .outputGuardrailClasses(OutputGuardrailFail.class)
                    .build();
        }

        static Assistant createUsingClassInstances() {
            return AiServices.builder(ClassLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrails(new InputGuardrailSuccess())
                    .outputGuardrails(new OutputGuardrailFail())
                    .build();
        }
    }

    interface SameMethodLevelAssistant extends Assistant {
        @InputGuardrails(InputGuardrailSuccess.class)
        @OutputGuardrails(OutputGuardrailFail.class)
        @Override
        String chat(String message);

        @InputGuardrails(InputGuardrailSuccess.class)
        @OutputGuardrails(OutputGuardrailFail.class)
        @Override
        String chat2(String message);

        static Assistant create() {
            return Assistant.create(SameMethodLevelAssistant.class);
        }

        static Assistant createUsingClassNames() {
            return AiServices.builder(SameMethodLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrailClasses(InputGuardrailSuccess.class)
                    .outputGuardrailClasses(OutputGuardrailFail.class)
                    .build();
        }

        static Assistant createUsingClassInstances() {
            return AiServices.builder(SameMethodLevelAssistant.class)
                    .chatModel(new MyChatModel())
                    .inputGuardrails(new InputGuardrailSuccess())
                    .outputGuardrails(new OutputGuardrailFail())
                    .build();
        }
    }

    interface MethodLevelAssistant extends Assistant {
        @InputGuardrails(InputGuardrailSuccess.class)
        @OutputGuardrails(OutputGuardrailSuccess.class)
        @Override
        String chat(String message);

        @InputGuardrails(InputGuardrailFail.class)
        @OutputGuardrails(OutputGuardrailFail.class)
        @Override
        String chat2(String message);

        static Assistant create() {
            return Assistant.create(MethodLevelAssistant.class);
        }
    }

    interface MethodLevelAssistant1 extends Assistant {
        @InputGuardrails({InputGuardrailSuccess.class, InputGuardrailFail.class})
        @OutputGuardrails(
                value = {OutputGuardrailSuccess.class, OutputGuardrailFail.class},
                maxRetries = 10)
        @Override
        String chat(String message);

        static Assistant create() {
            return Assistant.create(MethodLevelAssistant1.class);
        }
    }

    @InputGuardrails(InputGuardrailSuccess.class)
    @OutputGuardrails(OutputGuardrailSuccess.class)
    interface ClassAndMethodLevelAssistant extends Assistant {
        @InputGuardrails({InputGuardrailSuccess.class, InputGuardrailFail.class})
        @OutputGuardrails(
                value = {OutputGuardrailSuccess.class, OutputGuardrailFail.class},
                maxRetries = 10)
        @Override
        String chat(String message);

        static Assistant create() {
            return Assistant.create(ClassAndMethodLevelAssistant.class);
        }
    }

    public static class InputGuardrailSuccess implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return successWith(userMessage.singleText() + " from " + getClass().getSimpleName());
        }
    }

    static class RecordingInputGuardrail implements InputGuardrail {

        private final AtomicReference<UserMessage> observedUserMessage = new AtomicReference<>();

        @Override
        public InputGuardrailResult validate(InputGuardrailRequest request) {
            observedUserMessage.set(request.userMessage());
            return success();
        }

        UserMessage observedUserMessage() {
            return observedUserMessage.get();
        }
    }

    static class RewritingInputGuardrail implements InputGuardrail {

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return successWith("Rewritten prompt");
        }
    }

    public static class InputGuardrailFail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure(
                    userMessage.singleText() + " failure from " + getClass().getSimpleName());
        }
    }

    public static class OutputGuardrailSuccess implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            String successText = responseFromLLM.text() + " from " + getClass().getSimpleName();
            return successWith(responseFromLLM.withText(successText));
        }
    }

    public static class OutputGuardrailFail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure(
                    responseFromLLM.text() + " failure from " + getClass().getSimpleName());
        }
    }

    // --- non-streaming: reprompt + tool loop ---

    @Test
    void output_guardrail_reprompt_should_execute_tool_loop_before_revalidating() {
        var toolCallCount = new AtomicInteger(0);

        var toolCallResponse = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("verify")
                .arguments("{}")
                .build());

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from("bad response"), toolCallResponse, AiMessage.from("good response"));

        var tools = new Object() {
            @Tool("verify something")
            public String verify() {
                toolCallCount.incrementAndGet();
                return "verified";
            }
        };

        OutputGuardrail repromptOnBad = new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                if ("bad response".equals(responseFromLLM.text())) {
                    return reprompt("Invalid response", "Please try again using the verify tool");
                }
                return success();
            }
        };

        interface RepromptAssistant {
            String chat(String message);
        }

        RepromptAssistant assistant = AiServices.builder(RepromptAssistant.class)
                .chatModel(model)
                .tools(tools)
                .outputGuardrails(repromptOnBad)
                .build();

        String result = assistant.chat("Hello");

        assertThat(toolCallCount.get()).isEqualTo(1);
        assertThat(result).isEqualTo("good response");
    }

    @Test
    void output_guardrail_multi_reprompt_with_tool_loop_should_work() {
        var toolCallCount = new AtomicInteger(0);
        var repromptCount = new AtomicInteger(0);

        var toolCallResponse = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("verify")
                .arguments("{}")
                .build());

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from("bad response"),
                toolCallResponse,
                AiMessage.from("still bad"),
                AiMessage.from("good response"));

        var tools = new Object() {
            @Tool("verify something")
            public String verify() {
                toolCallCount.incrementAndGet();
                return "verified";
            }
        };

        OutputGuardrail repromptOnBad = new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                String text = responseFromLLM.text();
                if (text != null && text.contains("bad")) {
                    repromptCount.incrementAndGet();
                    return reprompt("Invalid response", "Please try again");
                }
                return success();
            }
        };

        interface RepromptAssistant {
            String chat(String message);
        }

        RepromptAssistant assistant = AiServices.builder(RepromptAssistant.class)
                .chatModel(model)
                .tools(tools)
                .outputGuardrails(repromptOnBad)
                .outputGuardrailsConfig(
                        OutputGuardrailsConfig.builder().maxRetries(3).build())
                .build();

        String result = assistant.chat("Hello");

        assertThat(result).isEqualTo("good response");
        assertThat(toolCallCount.get()).isEqualTo(1);
        assertThat(repromptCount.get()).isEqualTo(2);
    }

    @Test
    void output_guardrail_reprompt_with_failing_tool_should_send_error_to_llm() {
        var toolCallCount = new AtomicInteger(0);

        var toolCallResponse = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("verify")
                .arguments("{}")
                .build());

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from("bad response"), toolCallResponse, AiMessage.from("recovered"));

        var tools = new Object() {
            @Tool("verify something")
            public String verify() {
                toolCallCount.incrementAndGet();
                throw new RuntimeException("tool failed intentionally");
            }
        };

        OutputGuardrail repromptOnBad = new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                if ("bad response".equals(responseFromLLM.text())) {
                    return reprompt("Invalid response", "Please try again using the verify tool");
                }
                return success();
            }
        };

        interface RepromptAssistant {
            String chat(String message);
        }

        RepromptAssistant assistant = AiServices.builder(RepromptAssistant.class)
                .chatModel(model)
                .tools(tools)
                .outputGuardrails(repromptOnBad)
                .build();

        String result = assistant.chat("Hello");

        assertThat(result).isEqualTo("recovered");
        assertThat(toolCallCount.get()).isEqualTo(1);
    }

    // --- streaming: reprompt + tool loop ---

    @Test
    void streaming_output_guardrail_reprompt_should_execute_tool_loop_before_revalidating() throws Exception {
        var toolCallCount = new AtomicInteger(0);

        var toolCallResponse = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("verify")
                .arguments("{}")
                .build());

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from("bad response"), toolCallResponse, AiMessage.from("good response"));

        var tools = new Object() {
            @Tool("verify something")
            public String verify() {
                toolCallCount.incrementAndGet();
                return "verified";
            }
        };

        OutputGuardrail repromptOnBad = new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                if ("bad response".equals(responseFromLLM.text())) {
                    return reprompt("Invalid response", "Please try again using the verify tool");
                }
                return success();
            }
        };

        interface StreamingRepromptAssistant {
            TokenStream chat(String message);
        }

        StreamingRepromptAssistant assistant = AiServices.builder(StreamingRepromptAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .outputGuardrails(repromptOnBad)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("Hello")
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(5, TimeUnit.SECONDS);
        assertThat(toolCallCount.get()).isEqualTo(1);
        assertThat(response.aiMessage().text()).isEqualTo("good response");
    }

    @Test
    void streaming_output_guardrail_multi_reprompt_with_tool_loop_should_work() throws Exception {
        var toolCallCount = new AtomicInteger(0);
        var repromptCount = new AtomicInteger(0);

        var toolCallResponse = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("verify")
                .arguments("{}")
                .build());

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from("bad response"),
                toolCallResponse,
                AiMessage.from("still bad"),
                AiMessage.from("good response"));

        var tools = new Object() {
            @Tool("verify something")
            public String verify() {
                toolCallCount.incrementAndGet();
                return "verified";
            }
        };

        OutputGuardrail repromptOnBad = new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                String text = responseFromLLM.text();
                if (text != null && text.contains("bad")) {
                    repromptCount.incrementAndGet();
                    return reprompt("Invalid response", "Please try again");
                }
                return success();
            }
        };

        interface StreamingRepromptAssistant {
            TokenStream chat(String message);
        }

        StreamingRepromptAssistant assistant = AiServices.builder(StreamingRepromptAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .outputGuardrails(repromptOnBad)
                .outputGuardrailsConfig(
                        OutputGuardrailsConfig.builder().maxRetries(3).build())
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("Hello")
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("good response");
        assertThat(toolCallCount.get()).isEqualTo(1);
        assertThat(repromptCount.get()).isEqualTo(2);
    }

    @Test
    void streaming_output_guardrail_reprompt_with_failing_tool_should_send_error_to_llm() throws Exception {
        var toolCallCount = new AtomicInteger(0);

        var toolCallResponse = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("verify")
                .arguments("{}")
                .build());

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from("bad response"), toolCallResponse, AiMessage.from("recovered"));

        var tools = new Object() {
            @Tool("verify something")
            public String verify() {
                toolCallCount.incrementAndGet();
                throw new RuntimeException("tool failed intentionally");
            }
        };

        OutputGuardrail repromptOnBad = new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                if ("bad response".equals(responseFromLLM.text())) {
                    return reprompt("Invalid response", "Please try again using the verify tool");
                }
                return success();
            }
        };

        interface StreamingRepromptAssistant {
            TokenStream chat(String message);
        }

        StreamingRepromptAssistant assistant = AiServices.builder(StreamingRepromptAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .outputGuardrails(repromptOnBad)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("Hello")
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("recovered");
        assertThat(toolCallCount.get()).isEqualTo(1);
    }

    @Test
    void streaming_output_guardrail_reprompt_tool_loop_should_fire_tool_executed_event() throws Exception {
        // Guards against the streaming reprompt path skipping observability: tools executed while resolving a
        // reprompt must still fire ToolExecutedEvent, exactly like the synchronous path.
        List<ToolExecutedEvent> toolExecutedEvents = new CopyOnWriteArrayList<>();

        var toolCallResponse = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("verify")
                .arguments("{}")
                .build());

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from("bad response"), toolCallResponse, AiMessage.from("good response"));

        var tools = new Object() {
            @Tool("verify something")
            public String verify() {
                return "verified";
            }
        };

        OutputGuardrail repromptOnBad = new OutputGuardrail() {
            @Override
            public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                if ("bad response".equals(responseFromLLM.text())) {
                    return reprompt("Invalid response", "Please try again using the verify tool");
                }
                return success();
            }
        };

        interface StreamingRepromptAssistant {
            TokenStream chat(String message);
        }

        ToolExecutedEventListener listener = toolExecutedEvents::add;

        StreamingRepromptAssistant assistant = AiServices.builder(StreamingRepromptAssistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .outputGuardrails(repromptOnBad)
                .registerListener(listener)
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("Hello")
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(5, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("good response");
        assertThat(toolExecutedEvents).hasSize(1);
        assertThat(toolExecutedEvents.get(0).request().name()).isEqualTo("verify");
        assertThat(toolExecutedEvents.get(0).resultText()).isEqualTo("verified");
    }

    public static class MyChatModel implements ChatModel {
        private static String getUserMessage(ChatRequest chatRequest) {
            return chatRequest.messages().stream()
                    .filter(message -> message.type() == ChatMessageType.USER)
                    .findFirst()
                    .map(chatMessage -> ((UserMessage) chatMessage).singleText())
                    .orElseThrow(() -> new IllegalArgumentException("No user message found"));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Request: %s; Response: Hi!".formatted(getUserMessage(chatRequest))))
                    .build();
        }
    }
}
