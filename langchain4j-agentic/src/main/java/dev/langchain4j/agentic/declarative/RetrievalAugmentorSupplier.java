package dev.langchain4j.agentic.declarative;

import dev.langchain4j.rag.RetrievalAugmentor;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a method as a supplier of a retrieval augmentor that an agent can utilize during its operation.
 * The annotated method must be static, with no arguments, and return an instance of {@link RetrievalAugmentor}.
 * <p>
 * Example:
 * <pre>
 * {@code
 *     public interface ResearchAgent {
 *         @Agent("A research agent")
 *         String research(@V("topic") String topic);
 *
 *         @RetrievalAugmentorSupplier
 *         static RetrievalAugmentor retrievalAugmentor() {
 *             return RetrievalAugmentors.from(yourVectorStore);
 *         }
 *     }
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface RetrievalAugmentorSupplier {
}
