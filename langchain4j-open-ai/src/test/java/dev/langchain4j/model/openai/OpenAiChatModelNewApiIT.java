package dev.langchain4j.model.openai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatRequest;
import dev.langchain4j.model.chat.ChatResult;
import dev.langchain4j.model.chat.ModelParameters;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.model.chat.ResponseFormat.JSON;
import static dev.langchain4j.model.chat.ToolMode.ANY;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatModelNewApiIT {

    static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .addParameter("first", INTEGER)
            .addParameter("second", INTEGER)
            .build();

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    OpenAiChatModel visionModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    // TODO add more tests with more model params

    @Test
    void should_generate_answer() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResult chatResult = model.generate(chatRequest);

        // then
        assertThat(chatResult.aiMessage().text()).contains("Berlin");
        assertThat(chatResult.id()).isNotBlank();
        assertTokenUsage(chatResult.tokenUsage());
        assertThat(chatResult.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_length() {

        // given
        int maxTokens = 1;

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(ModelParameters.builder()
                        .maxTokens(maxTokens)
                        .build())
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResult chatResult = model.generate(chatRequest);

        // then
        assertThat(chatResult.aiMessage().text()).isNotBlank();

        assertThat(chatResult.id()).isNotBlank();

        TokenUsage tokenUsage = chatResult.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(chatResult.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("2+2=?"))
                .toolSpecifications(singletonList(calculator))
                .build();

        // when
        ChatResult chatResult = model.generate(chatRequest);

        // then
        AiMessage aiMessage = chatResult.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertThat(chatResult.id()).isNotBlank();
        assertTokenUsage(chatResult.tokenUsage());
        assertThat(chatResult.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        UserMessage.from("2+2=?"),
                        aiMessage,
                        ToolExecutionResultMessage.from(toolExecutionRequest, "4")
                )
                .build();

        // when
        ChatResult chatResult2 = model.generate(chatRequest2);

        // then
        AiMessage aiMessage2 = chatResult2.aiMessage();
        assertThat(aiMessage2.text()).contains("4");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        assertThat(chatResult2.id()).isNotBlank();
        assertTokenUsage(chatResult2.tokenUsage());
        assertThat(chatResult2.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_force_the_model_to_execute_specified_tool() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hi")) // message not related to tool on purpose
                .toolSpecifications(singletonList(calculator))
                .toolMode(ANY)
                .build();

        // when
        ChatResult chatResult = model.generate(chatRequest);

        // then
        AiMessage aiMessage = chatResult.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSizeGreaterThan(0);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isNotBlank();

        assertThat(chatResult.id()).isNotBlank();
        assertTokenUsage(chatResult.tokenUsage());
        assertThat(chatResult.finishReason()).isEqualTo(STOP); // not sure if a bug in OpenAI or stop is expected here
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("2+2=? 3+3=?"))
                .toolSpecifications(singletonList(calculator))
                .build();

        // when
        ChatResult chatResult = model.generate(chatRequest);

        // then
        AiMessage aiMessage = chatResult.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 = aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        assertThat(chatResult.id()).isNotBlank();
        assertTokenUsage(chatResult.tokenUsage());
        assertThat(chatResult.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        UserMessage.from("2+2=? 3+3=?"),
                        aiMessage,
                        ToolExecutionResultMessage.from(toolExecutionRequest1, "4"),
                        ToolExecutionResultMessage.from(toolExecutionRequest2, "6")
                )
                .toolSpecifications(singletonList(calculator))
                .build();

        // when
        ChatResult chatResult2 = model.generate(chatRequest2);

        // then
        AiMessage aiMessage2 = chatResult2.aiMessage();
        assertThat(aiMessage2.text()).contains("4", "6");
        assertThat(aiMessage2.toolExecutionRequests()).isNull();

        assertThat(chatResult2.id()).isNotBlank();
        assertTokenUsage(chatResult2.tokenUsage());
        assertThat(chatResult2.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_valid_json() {

        //given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(
                        "Return JSON with two fields: name and surname of Klaus Heisler. "
                                + "Before returning, tell me a joke." // nudging it to say something additionally to json
                ))
                .parameters(ModelParameters.builder()
                        .responseFormat(JSON)
                        .build())
                .build();

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        // when
        ChatResult chatResult = model.generate(chatRequest);

        // then
        assertThat(chatResult.aiMessage().text()).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void should_accept_image_url() {

        // given
        ImageContent imageContent = ImageContent.from(CAT_IMAGE_URL);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(imageContent))
                .build();

        // when
        ChatResult chatResult = visionModel.generate(chatRequest);

        // then
        assertThat(chatResult.aiMessage().text()).containsIgnoringCase("cat");
        assertThat(chatResult.id()).isNotBlank();
        assertTokenUsage(chatResult.tokenUsage());
        assertThat(chatResult.finishReason()).isEqualTo(STOP);
    }

//    @Test
//    void should_accept_base64_image() {
//
//        // given
//        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
//        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
//        UserMessage userMessage = UserMessage.from(imageContent);
//
//        // when
//        Response<AiMessage> response = visionModel.generate(userMessage);
//
//        // then
//        assertThat(response.content().text()).containsIgnoringCase("cat");
//
//        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(92);
//    }
//
//    @Test
//    void should_accept_text_and_image() {
//
//        // given
//        UserMessage userMessage = UserMessage.from(
//                TextContent.from("What do you see? Reply in one word."),
//                ImageContent.from(CAT_IMAGE_URL)
//        );
//
//        // when
//        Response<AiMessage> response = visionModel.generate(userMessage);
//
//        // then
//        assertThat(response.content().text()).containsIgnoringCase("cat");
//
//        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(102);
//    }
//
//    @Test
//    void should_accept_text_and_multiple_images() {
//
//        // given
//        UserMessage userMessage = UserMessage.from(
//                TextContent.from("What do you see? Reply with one word per image."),
//                ImageContent.from(CAT_IMAGE_URL),
//                ImageContent.from(DICE_IMAGE_URL)
//        );
//
//        // when
//        Response<AiMessage> response = visionModel.generate(userMessage);
//
//        // then
//        assertThat(response.content().text())
//                .containsIgnoringCase("cat")
//                .containsIgnoringCase("dice");
//
//        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(189);
//    }
//
//    @Test
//    void should_accept_text_and_multiple_images_from_different_sources() {
//
//        // given
//        UserMessage userMessage = UserMessage.from(
//                ImageContent.from(CAT_IMAGE_URL),
//                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
//                TextContent.from("What do you see? Reply with one word per image.")
//        );
//
//        // when
//        Response<AiMessage> response = visionModel.generate(userMessage);
//
//        // then
//        assertThat(response.content().text())
//                .containsIgnoringCase("cat")
//                .containsIgnoringCase("dice");
//
//        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(189);
//    }
//
//    @Test
//    void should_use_enum_as_model_name() {
//
//        // given
//        OpenAiChatModel model = OpenAiChatModel.builder()
//                .baseUrl(System.getenv("OPENAI_BASE_URL"))
//                .apiKey(System.getenv("OPENAI_API_KEY"))
//                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
//                .modelName(GPT_3_5_TURBO)
//                .logRequests(true)
//                .logResponses(true)
//                .build();
//
//        // when
//        String response = model.generate("What is the capital of Germany?");
//
//        // then
//        assertThat(response).containsIgnoringCase("Berlin");
//    }
//
//    @Test
//    void should_use_default_tokenizer() {
//
//        // when
//        int tokenCount = model.estimateTokenCount("Hello, how are you doing?");
//
//        // then
//        assertThat(tokenCount).isEqualTo(14);
//    }
//
//    @Test
//    void should_use_custom_tokenizer() {
//
//        // given
//
//        Tokenizer tokenizer = new Tokenizer() {
//
//            @Override
//            public int estimateTokenCountInText(String text) {
//                return 42;
//            }
//
//            @Override
//            public int estimateTokenCountInMessage(ChatMessage message) {
//                return 42;
//            }
//
//            @Override
//            public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
//                return 42;
//            }
//
//            @Override
//            public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
//                return 42;
//            }
//
//            @Override
//            public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
//                return 42;
//            }
//        };
//
//        OpenAiChatModel model = OpenAiChatModel.builder()
//                .apiKey("does not matter")
//                .tokenizer(tokenizer)
//                .build();
//
//        // when
//        int tokenCount = model.estimateTokenCount("Hello, how are you doing?");
//
//        // then
//        assertThat(tokenCount).isEqualTo(42);
//    }
//
//    @Test
//    void should_listen_request_and_response() {
//
//        // given
//        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
//        AtomicReference<ChatModelResponse> responseReference = new AtomicReference<>();
//
//        ChatModelListener listener = new ChatModelListener() {
//
//            @Override
//            public void onRequest(ChatModelRequestContext requestContext) {
//                requestReference.set(requestContext.request());
//                requestContext.attributes().put("id", "12345");
//            }
//
//            @Override
//            public void onResponse(ChatModelResponseContext responseContext) {
//                responseReference.set(responseContext.response());
//                assertThat(responseContext.request()).isSameAs(requestReference.get());
//                assertThat(responseContext.attributes().get("id")).isEqualTo("12345");
//            }
//
//            @Override
//            public void onError(ChatModelErrorContext errorContext) {
//                fail("onError() must not be called");
//            }
//        };
//
//        OpenAiChatModelName modelName = GPT_3_5_TURBO;
//        double temperature = 0.7;
//        double topP = 1.0;
//        int maxTokens = 7;
//
//        OpenAiChatModel model = OpenAiChatModel.builder()
//                .baseUrl(System.getenv("OPENAI_BASE_URL"))
//                .apiKey(System.getenv("OPENAI_API_KEY"))
//                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
//                .modelName(modelName)
//                .temperature(temperature)
//                .topP(topP)
//                .maxTokens(maxTokens)
//                .logRequests(true)
//                .logResponses(true)
//                .listeners(singletonList(listener))
//                .build();
//
//        UserMessage userMessage = UserMessage.from("hello");
//
//        ToolSpecification toolSpecification = ToolSpecification.builder()
//                .name("add")
//                .addParameter("a", INTEGER)
//                .addParameter("b", INTEGER)
//                .build();
//
//        // when
//        AiMessage aiMessage = model.generate(singletonList(userMessage), singletonList(toolSpecification)).content();
//
//        // then
//        ChatModelRequest request = requestReference.get();
//        assertThat(request.model()).isEqualTo(modelName.toString());
//        assertThat(request.temperature()).isEqualTo(temperature);
//        assertThat(request.topP()).isEqualTo(topP);
//        assertThat(request.maxTokens()).isEqualTo(maxTokens);
//        assertThat(request.messages()).containsExactly(userMessage);
//        assertThat(request.toolSpecifications()).containsExactly(toolSpecification);
//
//        ChatModelResponse response = responseReference.get();
//        assertThat(response.id()).isNotBlank();
//        assertThat(response.model()).isNotBlank();
//        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
//        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
//        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);
//        assertThat(response.finishReason()).isNotNull();
//        assertThat(response.aiMessage()).isEqualTo(aiMessage);
//    }
//
//    @Test
//    void should_listen_error() {
//
//        // given
//        String wrongApiKey = "banana";
//
//        AtomicReference<ChatModelRequest> requestReference = new AtomicReference<>();
//        AtomicReference<Throwable> errorReference = new AtomicReference<>();
//
//        ChatModelListener listener = new ChatModelListener() {
//
//            @Override
//            public void onRequest(ChatModelRequestContext requestContext) {
//                requestReference.set(requestContext.request());
//                requestContext.attributes().put("id", "12345");
//            }
//
//            @Override
//            public void onResponse(ChatModelResponseContext responseContext) {
//                fail("onResponse() must not be called");
//            }
//
//            @Override
//            public void onError(ChatModelErrorContext errorContext) {
//                errorReference.set(errorContext.error());
//                assertThat(errorContext.request()).isSameAs(requestReference.get());
//                assertThat(errorContext.partialResponse()).isNull();
//                assertThat(errorContext.attributes().get("id")).isEqualTo("12345");
//            }
//        };
//
//        OpenAiChatModel model = OpenAiChatModel.builder()
//                .apiKey(wrongApiKey)
//                .maxRetries(0)
//                .logRequests(true)
//                .logResponses(true)
//                .listeners(singletonList(listener))
//                .build();
//
//        String userMessage = "this message will fail";
//
//        // when
//        assertThrows(RuntimeException.class, () -> model.generate(userMessage));
//
//        // then
//        Throwable throwable = errorReference.get();
//        assertThat(throwable).isExactlyInstanceOf(OpenAiHttpException.class);
//        assertThat(throwable).hasMessageContaining("Incorrect API key provided");
//    }

    private static void assertTokenUsage(TokenUsage tokenUsage) {
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
