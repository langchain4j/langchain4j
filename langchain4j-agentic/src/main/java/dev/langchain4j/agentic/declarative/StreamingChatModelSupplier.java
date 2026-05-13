package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * Marks a method as a supplier of the streaming chat model to be used by an agent.
 * The method must be static and return an instance of {@link StreamingChatModel}.
 * <p>
 * When the method has no parameters, it is invoked once at build time to provide a fixed model.
 * When the method has parameters annotated with {@link dev.langchain4j.service.V @V},
 * they are resolved from the current {@link dev.langchain4j.agentic.scope.AgenticScope AgenticScope}
 * at each invocation, enabling dynamic model selection based on runtime state.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface StreamingCreativeWriter {
 *
 *         @UserMessage("""
 *                 You are a creative writer.
 *                 Generate a draft of a story long no more than 3 sentence around the given topic.
 *                 Return only the story and nothing else.
 *                 The topic is {{topic}}.
 *                 """)
 *         @Agent(description = "Generate a story based on the given topic", outputKey = "story")
 *         TokenStream generateStory(@V("topic") String topic);
 *
 *         @StreamingChatModelSupplier
 *         static StreamingChatModel chatModel() {
 *             return OpenAiStreamingChatModel.builder()
 *                     .baseUrl(System.getenv("OPENAI_BASE_URL"))
 *                     .apiKey(System.getenv("OPENAI_API_KEY"))
 *                     .modelName(OpenAiChatModelName.GPT_4_O_MINI)
 *                     .temperature(0.0)
 *                     .logRequests(true)
 *                     .build();
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface StreamingChatModelSupplier {
}
