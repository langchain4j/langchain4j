package dev.langchain4j.test.model;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.test.LangChain4jAssertions;
import dev.langchain4j.test.internal.ChatMessageTexts;
import java.util.List;
import org.assertj.core.api.AbstractObjectAssert;

/**
 * Assertions on a request sent to a chat model: its messages and the tools it offers. Use them to check what an AI
 * Service sent to the model, for example that the system message and the prompt template were filled in as expected.
 * To get the requests, register a {@code ChatModelListener} on the chat model that the AI Service uses:
 * <pre>{@code
 * import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
 *
 * List<ChatRequest> requests = new CopyOnWriteArrayList<>();
 * ChatModelListener requestCollector = new ChatModelListener() {
 *     @Override
 *     public void onRequest(ChatModelRequestContext requestContext) {
 *         requests.add(requestContext.chatRequest());
 *     }
 * };
 * // for example OpenAiChatModel.builder()...listeners(requestCollector).build()
 *
 * assistant.chat("Cancel booking MS-777");
 *
 * assertThat(requests.get(0))
 *         .hasSystemMessageContaining("support agent")
 *         .hasUserMessageContaining("MS-777")
 *         .hasToolSpecification("cancelBooking");
 * }</pre>
 * When a framework creates the chat model for you, register the listener the way that framework expects, for example
 * as a bean. When an assertion fails, the message lists all messages or tools of the request; each message text is
 * cut to 1000 characters.
 */
@Experimental
public final class ChatRequestAssert extends AbstractObjectAssert<ChatRequestAssert, ChatRequest> {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    /**
     * Creates assertions on the given chat request. Prefer
     * {@link LangChain4jAssertions#assertThat(ChatRequest)}.
     */
    public ChatRequestAssert(ChatRequest actual) {
        super(actual, ChatRequestAssert.class);
    }

    /**
     * Asserts that the request has a system message containing the given text.
     */
    public ChatRequestAssert hasSystemMessageContaining(String text) {
        ensureNotNull(text, "text");
        isNotNull();
        boolean found = actual.messages().stream()
                .anyMatch(message -> message instanceof SystemMessage systemMessage
                        && systemMessage.text().contains(text));
        if (!found) {
            throw failure(
                    "Expected the request to have a system message containing:%n  %s%nbut its messages were:%n%s",
                    text, describeMessages());
        }
        return myself;
    }

    /**
     * Asserts that the request has a user message containing the given text. All user messages of the request are
     * checked, including those from the chat memory, not only the last one: to check only the message of the current
     * turn, use {@link #hasLastUserMessageContaining(String)}. The text of a user message includes what the AI Service
     * added to it, for example the retrieved contents.
     */
    public ChatRequestAssert hasUserMessageContaining(String text) {
        ensureNotNull(text, "text");
        isNotNull();
        boolean found = actual.messages().stream()
                .map(ChatMessageTexts::userText)
                .anyMatch(userText -> userText != null && userText.contains(text));
        if (!found) {
            throw failure(
                    "Expected the request to have a user message containing:%n  %s%nbut its messages were:%n%s",
                    text, describeMessages());
        }
        return myself;
    }

    /**
     * Asserts that the last user message of the request contains the given text. In the first request of an AI Service
     * invocation, this is the message of the current turn: the messages from the chat memory come before it. The text
     * of a user message includes what the AI Service added to it, for example the retrieved contents.
     */
    public ChatRequestAssert hasLastUserMessageContaining(String text) {
        ensureNotNull(text, "text");
        isNotNull();
        String lastUserText = UserMessage.findLast(actual.messages())
                .map(ChatMessageTexts::userText)
                .orElse(null);
        if (lastUserText == null || !lastUserText.contains(text)) {
            throw failure(
                    "Expected the last user message of the request to contain:%n  %s%nbut its messages were:%n%s",
                    text, describeMessages());
        }
        return myself;
    }

    /**
     * Asserts that the request offers the model the tool with the given name.
     */
    public ChatRequestAssert hasToolSpecification(String toolName) {
        ensureNotBlank(toolName, "toolName");
        isNotNull();
        List<String> toolNames = toolNames();
        if (!toolNames.contains(toolName)) {
            throw failure(
                    "Expected the request to offer the tool %s, but it offered %s",
                    toolName, toolNames.isEmpty() ? "no tools" : toolNames);
        }
        return myself;
    }

    private List<String> toolNames() {
        List<ToolSpecification> toolSpecifications = actual.toolSpecifications();
        return toolSpecifications == null
                ? List.of()
                : toolSpecifications.stream().map(ToolSpecification::name).toList();
    }

    private String describeMessages() {
        List<ChatMessage> messages = actual.messages();
        return ChatMessageTexts.describe(messages, MAX_MESSAGE_LENGTH).indent(2).stripTrailing();
    }
}
