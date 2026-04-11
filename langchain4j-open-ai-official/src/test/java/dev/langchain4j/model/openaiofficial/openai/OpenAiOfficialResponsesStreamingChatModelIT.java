package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.openaiofficial.openai.InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME_ALTERNATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.ResponsesModel;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileDeleted;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.Tool;
import com.openai.models.vectorstores.VectorStore;
import com.openai.models.vectorstores.VectorStoreCreateParams;
import com.openai.models.vectorstores.filebatches.FileBatchCreateParams;
import com.openai.models.vectorstores.filebatches.FileBatchRetrieveParams;
import com.openai.models.vectorstores.filebatches.VectorStoreFileBatch;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialServerTool;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialServerToolResult;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final String RESPONSES_MODEL_NAME = "gpt-5.4";
    private static final long VECTOR_STORE_TIMEOUT_SECONDS = 300;
    private static final long DEFAULT_RESPONSE_TIMEOUT_SECONDS = 180;

    @Override
    protected List<StreamingChatModel> models() {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("gpt-5-mini")
                .build();

        return List.of(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        OpenAiOfficialResponsesStreamingChatModel.Builder modelBuilder =
                OpenAiOfficialResponsesStreamingChatModel.builder().client(client);

        if (parameters.modelName() != null) {
            modelBuilder.modelName(parameters.modelName());
        } else {
            modelBuilder.modelName(CHAT_MODEL_NAME_ALTERNATE.toString());
        }

        if (parameters instanceof OpenAiOfficialChatRequestParameters openAiParams) {
            if (openAiParams.temperature() != null) {
                modelBuilder.temperature(openAiParams.temperature());
            }
            if (openAiParams.topP() != null) {
                modelBuilder.topP(openAiParams.topP());
            }
            if (openAiParams.maxOutputTokens() != null) {
                modelBuilder.maxOutputTokens(openAiParams.maxOutputTokens());
            }
        }

        return modelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        // Ensure minimum of 16 tokens for Responses API
        int effectiveMaxTokens = Math.max(maxOutputTokens, 16);
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(effectiveMaxTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiOfficialTokenUsage.class;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .listeners(listener)
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\"city\":\"Munich\"}"));
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\":\"France\"}"));
    }

    @Override
    protected void should_respect_modelName_in_chat_request(StreamingChatModel model) {
        // Responses API requires minimum of 16 tokens, override to use 16 instead of 1
        String modelName = customModelName();

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .modelName(modelName)
                .maxOutputTokens(16)
                .build();

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(UserMessage.from("Tell me a story"))
                        .parameters(parameters)
                        .build();

        dev.langchain4j.model.chat.response.ChatResponse chatResponse =
                chat(model, chatRequest).chatResponse();

        assertThat(chatResponse.aiMessage().text()).isNotBlank();
        assertThat(chatResponse.metadata().modelName()).isEqualTo(modelName);
    }

    @Override
    protected void should_respect_maxOutputTokens_in_chat_request(StreamingChatModel model) {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .parameters(parameters)
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Override
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();

        StreamingChatModel model = createModelWith(parameters);
        if (model == null) {
            return;
        }

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Override
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            StreamingChatModel model) {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .parameters(parameters)
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Override
    @Disabled("Responses API does not support stop sequences")
    protected void should_respect_stopSequences_in_chat_request(StreamingChatModel model) {}

    @Override
    @Disabled("Responses API does not support stop sequences")
    protected void should_respect_stopSequences_in_default_model_parameters() {}

    @Override
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {
        // Responses API requires minimum of 16 tokens, so we use 16 instead of 5
        int maxOutputTokens = 16;
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);

        StreamingChatModel model = createModelWith(parameters);

        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .parameters(parameters)
                        .messages(dev.langchain4j.data.message.UserMessage.from("Tell me a long story"))
                        .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        dev.langchain4j.model.chat.response.ChatResponse chatResponse = handler.get();

        dev.langchain4j.data.message.AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            dev.langchain4j.model.output.TokenUsage tokenUsage =
                    chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Test
    void should_work_with_o_models() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("o4-mini")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_support_strict_mode_false() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .strict(false)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is 2+2?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
    }

    @Test
    void should_support_reasoning_effort() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName("o4-mini")
                .reasoningEffort("medium")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Paris");
    }

    @Test
    void should_support_max_tool_calls() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .maxToolCalls(1)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the weather?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
    }

    @Test
    void should_support_parallel_tool_calls_disabled() {

        // given
        var client = OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(client)
                .modelName(InternalOpenAiOfficialTestHelper.CHAT_MODEL_NAME.toString())
                .parallelToolCalls(false)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        // then
        assertThat(handler.get().aiMessage().text()).isNotBlank();
    }

    @Test
    void should_return_server_tool_results_for_web_search() {

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(newClient())
                .modelName(RESPONSES_MODEL_NAME)
                .serverTools(OpenAiOfficialServerTool.builder()
                        .type("web_search")
                        .addAttribute("search_context_size", "low")
                        .addAttribute("filters", Map.of("allowed_domains", List.of("openai.com")))
                        .build())
                .returnServerToolResults(true)
                .build();

        AiMessage aiMessage = chatForAiMessage(
                model,
                "Use web search on openai.com to find the GPT-5.4 documentation page and answer with a one-sentence summary.");
        assertThat(aiMessage.text()).isNotBlank();
        assertServerToolResultTypes(aiMessage, "web_search_call");
    }

    @Test
    void should_return_server_tool_results_for_shell() throws Exception {

        String expectedToken = "shell-proof-" + UUID.randomUUID();
        StreamingChatModel model = responsesModelWithServerTools(OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of("type", "container_auto"))
                .build());

        AiMessage aiMessage = chatForAiMessage(
                model,
                "Use the shell tool to run a command that prints '" + expectedToken
                        + "' exactly once, then reply with only that token.");

        assertThat(aiMessage.text()).contains(expectedToken);
        assertServerToolResultTypes(aiMessage, "shell_call");
    }

    @Test
    void should_return_server_tool_results_for_file_search() {
        OpenAIClient client = newClient();
        String expectedToken = "file-search-proof-" + UUID.randomUUID();
        String filterValue = "it-" + UUID.randomUUID();
        TestVectorStoreResource resource =
                createVectorStoreResource(client, "The recovery token is " + expectedToken + ".", filterValue);

        try {
            StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                    .client(client)
                    .modelName(RESPONSES_MODEL_NAME)
                    .maxToolCalls(1)
                    .serverTools(OpenAiOfficialServerTool.builder()
                            .type("file_search")
                            .addAttribute("vector_store_ids", List.of(resource.vectorStoreId()))
                            .addAttribute("filters", Map.of(
                                    "type", "eq",
                                    "key", "scope",
                                    "value", filterValue))
                            .addAttribute("max_num_results", 3)
                            .build())
                    .returnServerToolResults(true)
                    .build();

            AiMessage aiMessage = chatForAiMessage(
                    model,
                    "What is the recovery token in the uploaded document? Use file search once, then reply with only the token.");

            assertThat(aiMessage.text()).contains(expectedToken);
            assertServerToolResultTypes(aiMessage, "file_search_call");
        } finally {
            resource.cleanup(client);
        }
    }

    @Test
    void should_return_server_tool_results_for_tool_search() {

        StreamingChatModel model = responsesModelWithServerTools(
                OpenAiOfficialServerTool.builder()
                        .type("namespace")
                        .name("github")
                        .addAttribute("description", "GitHub tools")
                        .addAttribute("tools", List.of(Map.of(
                                "type", "function",
                                "name", "search_code",
                                "description", "Search code in a repository",
                                "defer_loading", true,
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "query", Map.of("type", "string")),
                                        "required", List.of("query")))))
                        .build(),
                OpenAiOfficialServerTool.builder()
                        .type("tool_search")
                        .build());

        AiMessage aiMessage = chatForAiMessage(
                model,
                "Use tool search to identify which namespace contains a code search tool, and answer with only the namespace name.");

        assertServerToolResultTypes(aiMessage, "tool_search_call");
    }

    @Test
    void should_return_server_tool_results_for_mcp() {
        Response response = createDirectResponseWithRetries(
                "Roll 2d4+1",
                OpenAiOfficialServerTool.builder()
                        .type("mcp")
                        .name("dmcp")
                        .addAttribute("server_label", "dmcp")
                        .addAttribute("server_description", "A Dungeons and Dragons MCP server to assist with dice rolling.")
                        .addAttribute("server_url", "https://dmcp-server.deno.dev/sse")
                        .addAttribute("allowed_tools", List.of("roll"))
                        .addAttribute("require_approval", "never")
                        .build());

        assertThat(response.output())
                .extracting(item -> item.isMcpListTools() ? "mcp_list_tools" : item.isMcpCall() ? "mcp_call" : null)
                .contains("mcp_list_tools", "mcp_call");

        assertThat(response.output().stream()
                .filter(ResponseOutputItem::isMcpCall)
                .map(ResponseOutputItem::asMcpCall)
                .findFirst())
                .isPresent()
                .get()
                .satisfies(mcpCall -> {
                    assertThat(mcpCall.name()).isEqualTo("roll");
                    assertThat(mcpCall.serverLabel()).isEqualTo("dmcp");
                    assertThat(mcpCall.status()).hasValueSatisfying(status -> assertThat(status.asString()).isEqualTo("completed"));
                    assertThat(mcpCall.output())
                            .hasValueSatisfying(output -> assertThat(Integer.parseInt(output)).isBetween(3, 9));
                });
    }

    @Test
    void should_return_server_tool_results_for_computer() {
        Response response = createDirectResponse(
                "Open data:text/html,%3Ch1%3EComputer%20Tool%20Ready%3C/h1%3E and stop after the first computer action.",
                OpenAiOfficialServerTool.builder()
                        .type("computer")
                        .build());

        assertThat(response.output())
                .extracting(ResponseOutputItem::isComputerCall)
                .contains(true);

        assertThat(response.output().stream()
                .filter(ResponseOutputItem::isComputerCall)
                .map(ResponseOutputItem::asComputerCall)
                .findFirst())
                .isPresent()
                .get()
                .satisfies(computerCall -> {
                    assertThat(computerCall.status().asString()).isEqualTo("completed");
                    assertThat(computerCall.actions()).isPresent();
                });
    }

    private static OpenAIClient newClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    private static Response createDirectResponse(String input, OpenAiOfficialServerTool... serverTools) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(ResponsesModel.ofChat(ChatModel.of(RESPONSES_MODEL_NAME)))
                .input(input);

        for (OpenAiOfficialServerTool serverTool : serverTools) {
            builder.addTool(invokeToResponsesServerTool(serverTool));
        }

        return newClient().responses().create(builder.build());
    }

    private static Response createDirectResponseWithRetries(String input, OpenAiOfficialServerTool... serverTools) {
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return createDirectResponse(input, serverTools);
            } catch (RuntimeException e) {
                lastException = e;
                String message = rootCauseMessage(e);
                boolean retryableMcpFailure = message != null
                        && ((message.contains("Error retrieving tool list from MCP server") && message.contains("424"))
                                || message.toLowerCase().contains("timed out")
                                || message.toLowerCase().contains("timeout"));
                if (!retryableMcpFailure || attempt == 5) {
                    throw e;
                }
                try {
                    TimeUnit.SECONDS.sleep(attempt * 3L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interruptedException);
                }
            }
        }

        throw lastException;
    }

    private static StreamingChatModel responsesModelWithServerTools(OpenAiOfficialServerTool... serverTools) {
        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .client(newClient())
                .modelName(RESPONSES_MODEL_NAME)
                .serverTools(serverTools)
                .returnServerToolResults(true)
                .build();
    }

    private static AiMessage chatForAiMessage(StreamingChatModel model, String prompt) {
        return chatForAiMessage(model, prompt, DEFAULT_RESPONSE_TIMEOUT_SECONDS);
    }

    private static AiMessage chatForAiMessageWithRetries(StreamingChatModel model, String prompt) {
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return chatForAiMessage(model, prompt, DEFAULT_RESPONSE_TIMEOUT_SECONDS);
            } catch (RuntimeException e) {
                lastException = e;
                String message = rootCauseMessage(e);
                boolean retryableMcpFailure = message != null
                        && message.contains("Error retrieving tool list from MCP server")
                        && message.contains("424");
                if (!retryableMcpFailure || attempt == 3) {
                    throw e;
                }
                try {
                    TimeUnit.SECONDS.sleep(attempt * 2L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(interruptedException);
                }
            }
        }

        throw lastException;
    }

    private static AiMessage chatForAiMessage(StreamingChatModel model, String prompt, long timeoutSeconds) {
        TestStreamingChatResponseHandler handler = new ExtendedTimeoutStreamingChatResponseHandler(timeoutSeconds);
        model.chat(prompt, handler);
        return handler.get().aiMessage();
    }

    @SuppressWarnings("unchecked")
    private static List<OpenAiOfficialServerToolResult> serverToolResults(AiMessage aiMessage) {
        List<OpenAiOfficialServerToolResult> results = aiMessage.attribute("server_tool_results", List.class);
        assertThat(results).isNotNull().isNotEmpty();
        return results;
    }

    private static void assertServerToolResultTypes(AiMessage aiMessage, String... expectedTypes) {
        assertThat(serverToolResults(aiMessage))
                .extracting(OpenAiOfficialServerToolResult::type)
                .contains(expectedTypes);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static TestVectorStoreResource createVectorStoreResource(OpenAIClient client, String content, String filterValue) {
        try {
            Path tempFile = Files.createTempFile("openai-file-search-", ".txt");
            Files.writeString(tempFile, content);

            FileObject fileObject = client.files().create(FileCreateParams.builder()
                    .file(tempFile)
                    .purpose(FilePurpose.USER_DATA)
                    .build());

            VectorStore vectorStore = client.vectorStores().create(VectorStoreCreateParams.builder()
                    .name("langchain4j-file-search-it-" + UUID.randomUUID())
                    .build());

            VectorStoreFileBatch batch = client.vectorStores().fileBatches().create(FileBatchCreateParams.builder()
                    .vectorStoreId(vectorStore.id())
                    .addFile(FileBatchCreateParams.File.builder()
                            .fileId(fileObject.id())
                            .attributes(FileBatchCreateParams.File.Attributes.builder()
                                    .putAdditionalProperty("scope", com.openai.core.JsonValue.from(filterValue))
                                    .build())
                            .build())
                    .build());

            waitForVectorStoreBatchReady(client, vectorStore.id(), batch.id());
            return new TestVectorStoreResource(tempFile, fileObject.id(), vectorStore.id());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void waitForVectorStoreBatchReady(OpenAIClient client, String vectorStoreId, String batchId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(VECTOR_STORE_TIMEOUT_SECONDS);
        String lastStatus = "unknown";

        while (System.nanoTime() < deadline) {
            VectorStoreFileBatch batch = client.vectorStores().fileBatches().retrieve(FileBatchRetrieveParams.builder()
                    .vectorStoreId(vectorStoreId)
                    .batchId(batchId)
                    .build());
            String status = batch.status().asString();
            lastStatus = status;
            if ("completed".equals(status)) {
                return;
            }
            if ("failed".equals(status) || "cancelled".equals(status) || "expired".equals(status)) {
                throw new IllegalStateException("Vector store batch " + batchId + " ended in status " + status);
            }
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        throw new IllegalStateException(
                "Timed out waiting for vector store batch " + batchId + " to become ready; last status was " + lastStatus);
    }

    private static final class ExtendedTimeoutStreamingChatResponseHandler extends TestStreamingChatResponseHandler {

        private final CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        private final long timeoutSeconds;

        private ExtendedTimeoutStreamingChatResponseHandler(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            super.onCompleteResponse(completeResponse);
            futureResponse.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            super.onError(error);
            futureResponse.completeExceptionally(error);
        }

        @Override
        public ChatResponse get() {
            try {
                return futureResponse.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private record TestVectorStoreResource(Path tempFile, String fileId, String vectorStoreId) {

        void cleanup(OpenAIClient client) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // Best-effort cleanup for integration resources.
            }
            try {
                client.vectorStores().delete(vectorStoreId);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup for integration resources.
            }
            try {
                FileDeleted deleted = client.files().delete(fileId);
                assertThat(deleted.deleted()).isTrue();
            } catch (RuntimeException ignored) {
                // Best-effort cleanup for integration resources.
            }
        }
    }

    private static Tool invokeToResponsesServerTool(OpenAiOfficialServerTool serverTool) {
        try {
            Class<?> mapperClass = Class.forName("dev.langchain4j.model.openaiofficial.OpenAiOfficialServerToolMapper");
            Method method = mapperClass.getDeclaredMethod("toResponsesTool", OpenAiOfficialServerTool.class);
            method.setAccessible(true);
            return (Tool) method.invoke(null, serverTool);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false;
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return false;
    }

    @Override
    protected void should_fail_if_images_as_public_URLs_are_not_supported(StreamingChatModel model) {

        // given
        UserMessage userMessage =
                UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(catImageUrl()));
        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(userMessage)
                        .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest));
    }

    @Override
    protected boolean supportsPartialToolStreaming(dev.langchain4j.model.chat.StreamingChatModel model) {
        return false;
    }

    @Override
    @Disabled("Can't do it reliably")
    protected void should_execute_multiple_tools_in_parallel_then_answer(StreamingChatModel model) {
    }
}
