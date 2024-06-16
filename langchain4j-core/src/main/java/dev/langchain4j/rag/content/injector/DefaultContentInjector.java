package dev.langchain4j.rag.content.injector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import lombok.Builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

/**
 * Default implementation of {@link ContentInjector} intended to be suitable for the majority of use cases.
 * <br>
 * <br>
 * It's important to note that while efforts will be made to avoid breaking changes,
 * the default behavior of this class may be updated in the future if it's found
 * that the current behavior does not adequately serve the majority of use cases.
 * Such changes would be made to benefit both current and future users.
 * <br>
 * <br>
 * This implementation appends all given {@link Content}s to the end of the given {@link UserMessage}
 * in their order of iteration.
 * Refer to {@link #DEFAULT_PROMPT_TEMPLATE} and implementation for more details.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #promptTemplate}: The prompt template that defines how the original {@code userMessage}
 * and {@code contents} are combined into the resulting {@link UserMessage}.
 * <br>
 * - {@link #metadataKeysToInclude}: A list of {@link Metadata} keys that should be included
 * with each {@link Content#textSegment()}.
 */
public class DefaultContentInjector implements ContentInjector {

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "{{userMessage}}\n" +
                    "\n" +
                    "Answer using the following information:\n" +
                    "{{contents}}"
    );

    private final PromptTemplate promptTemplate;
    private final List<String> metadataKeysToInclude;

    public DefaultContentInjector() {
        this(DEFAULT_PROMPT_TEMPLATE, null);
    }

    public DefaultContentInjector(List<String> metadataKeysToInclude) {
        this(DEFAULT_PROMPT_TEMPLATE, ensureNotEmpty(metadataKeysToInclude, "metadataKeysToInclude"));
    }

    public DefaultContentInjector(PromptTemplate promptTemplate) {
        this(ensureNotNull(promptTemplate, "promptTemplate"), null);
    }

    @Builder
    public DefaultContentInjector(PromptTemplate promptTemplate, List<String> metadataKeysToInclude) {
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.metadataKeysToInclude = copyIfNotNull(metadataKeysToInclude);
    }

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {

        if (contents.isEmpty()) {
            return chatMessage;
        }

        Prompt prompt = createPrompt(chatMessage, contents);
        if (chatMessage instanceof UserMessage && isNotNullOrBlank(((UserMessage)chatMessage).name())) {
            return prompt.toUserMessage(((UserMessage)chatMessage).name());
        }

        return prompt.toUserMessage();
    }

    protected Prompt createPrompt(ChatMessage chatMessage, List<Content> contents) {
        return createPrompt((UserMessage) chatMessage, contents);
    }

    /**
     * @deprecated use {@link #inject(List, ChatMessage)} instead.
     */
    @Override
    @Deprecated
    public UserMessage inject(List<Content> contents, UserMessage userMessage) {

        if (contents.isEmpty()) {
            return userMessage;
        }

        Prompt prompt = createPrompt(userMessage, contents);
        if (isNotNullOrBlank(userMessage.name())) {
            return prompt.toUserMessage(userMessage.name());
        }
        return prompt.toUserMessage();
    }

    /**
     * @deprecated implement/override {@link #createPrompt(ChatMessage, List)} instead.
     */
    @Deprecated
    protected Prompt createPrompt(UserMessage userMessage, List<Content> contents) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("userMessage", userMessage.text());
        variables.put("contents", format(contents));
        return promptTemplate.apply(variables);
    }

    protected String format(List<Content> contents) {
        return contents.stream()
                .map(this::format)
                .collect(joining("\n\n"));
    }

    protected String format(Content content) {

        TextSegment segment = content.textSegment();

        if (isNullOrEmpty(metadataKeysToInclude)) {
            return segment.text();
        }

        String segmentContent = segment.text();
        String segmentMetadata = format(segment.metadata());

        return format(segmentContent, segmentMetadata);
    }

    protected String format(Metadata metadata) {
        StringBuilder formattedMetadata = new StringBuilder();
        for (String metadataKey : metadataKeysToInclude) {
            String metadataValue = metadata.get(metadataKey);
            if (metadataValue != null) {
                if (formattedMetadata.length() > 0) {
                    formattedMetadata.append("\n");
                }
                formattedMetadata.append(metadataKey).append(": ").append(metadataValue);
            }
        }
        return formattedMetadata.toString();
    }

    protected String format(String segmentContent, String segmentMetadata) {
        return segmentMetadata.isEmpty()
                ? segmentContent
                : String.format("content: %s\n%s", segmentContent, segmentMetadata);
    }
}
