package dev.langchain4j.rag;

import dev.langchain4j.Experimental;
import dev.langchain4j.context.ContextManager;
import dev.langchain4j.context.ContextProvider;
import dev.langchain4j.context.ContextRequest;
import dev.langchain4j.context.ContextResult;
import dev.langchain4j.context.DefaultContextManager;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A {@link RetrievalAugmentor} that adds Context-Augmented Generation (CAG)
 * capabilities on top of the existing RAG pipeline.
 * <p>
 * This implementation orchestrates the flow between:
 * <ul>
 *   <li>A {@link ContextManager} — gathers contextual content</li>
 *   <li>An optional delegate {@link RetrievalAugmentor} — performs retrieval (RAG)</li>
 *   <li>A {@link ContentInjector} — injects all content into the message</li>
 * </ul>
 * <p>
 * Three usage modes:
 * <ul>
 *   <li><b>Pure CAG</b>: Only context providers, no retrieval.
 *       Context alone augments the message.</li>
 *   <li><b>Hybrid CAG+RAG</b>: Context providers and a delegate
 *       {@link RetrievalAugmentor}. Both context and retrieved content
 *       augment the message.</li>
 *   <li><b>Context-gated retrieval</b>: Context is gathered first;
 *       the {@link ContextResult#isRetrievalAdvised()} flag determines
 *       whether the delegate is invoked.</li>
 * </ul>
 * <p>
 * Context is propagated to downstream RAG components via a well-known
 * key in {@link dev.langchain4j.invocation.InvocationParameters}: {@link #CONTEXT_KEY}.
 * <p>
 * Plugs into AiServices without any changes:
 * <pre>
 * AiServices.builder(Assistant.class)
 *     .retrievalAugmentor(contextAwareAugmentor)
 *     .build();
 * </pre>
 *
 * @see ContextManager
 * @see ContextProvider
 * @see DefaultRetrievalAugmentor
 */
@Experimental
public class ContextAwareRetrievalAugmentor implements RetrievalAugmentor {

    /**
     * The well-known key in {@link dev.langchain4j.invocation.InvocationParameters} under which
     * resolved context is stored. Downstream RAG components can access it via:
     * <pre>
     * query.metadata().invocationParameters().get(ContextAwareRetrievalAugmentor.CONTEXT_KEY);
     * </pre>
     * The value is a {@code List<Content>}.
     */
    public static final String CONTEXT_KEY = "langchain4j.context";

    private static final Logger log = LoggerFactory.getLogger(ContextAwareRetrievalAugmentor.class);

    private final ContextManager contextManager;
    private final RetrievalAugmentor delegate;
    private final ContentInjector contentInjector;

    private ContextAwareRetrievalAugmentor(
            ContextManager contextManager,
            RetrievalAugmentor delegate,
            ContentInjector contentInjector) {
        this.contextManager = ensureNotNull(contextManager, "contextManager");
        this.delegate = delegate;
        this.contentInjector = getOrDefault(contentInjector, DefaultContentInjector::new);
    }

    @Override
    public AugmentationResult augment(AugmentationRequest augmentationRequest) {

        ContextRequest contextRequest = new ContextRequest(
                augmentationRequest.chatMessage(),
                augmentationRequest.metadata());

        ContextResult contextResult = contextManager.resolveContext(contextRequest);
        List<Content> contextContents = contextResult.contents();

        log.trace("Resolved {} context content(s)", contextContents.size());

        if (augmentationRequest.metadata().invocationParameters() != null) {
            augmentationRequest.metadata().invocationParameters().put(CONTEXT_KEY, contextContents);
        }

        List<Content> retrievedContents = List.of();
        if (contextResult.isRetrievalAdvised() && delegate != null) {
            AugmentationResult ragResult = delegate.augment(augmentationRequest);
            retrievedContents = ragResult.contents();
            log.trace("Retrieved {} content(s) from delegate", retrievedContents.size());
        }

        List<Content> allContents = new ArrayList<>(contextContents.size() + retrievedContents.size());
        allContents.addAll(contextContents);
        allContents.addAll(retrievedContents);

        var augmentedMessage = contentInjector.inject(allContents, augmentationRequest.chatMessage());

        return AugmentationResult.builder()
                .chatMessage(augmentedMessage)
                .contents(allContents)
                .build();
    }

    public static ContextAwareRetrievalAugmentorBuilder builder() {
        return new ContextAwareRetrievalAugmentorBuilder();
    }

    public static class ContextAwareRetrievalAugmentorBuilder {

        private ContextManager contextManager;
        private final List<ContextProvider> contextProviders = new ArrayList<>();
        private RetrievalAugmentor delegate;
        private ContentInjector contentInjector;

        ContextAwareRetrievalAugmentorBuilder() {
        }

        /**
         * Sets the {@link ContextManager} to use. Mutually exclusive with
         * {@link #contextProvider(ContextProvider)} and {@link #contextProviders(List)}.
         */
        public ContextAwareRetrievalAugmentorBuilder contextManager(ContextManager contextManager) {
            this.contextManager = contextManager;
            return this;
        }

        /**
         * Adds a {@link ContextProvider}. Multiple providers can be added and will
         * be composed into a {@link DefaultContextManager}.
         * Mutually exclusive with {@link #contextManager(ContextManager)}.
         */
        public ContextAwareRetrievalAugmentorBuilder contextProvider(ContextProvider contextProvider) {
            this.contextProviders.add(ensureNotNull(contextProvider, "contextProvider"));
            return this;
        }

        /**
         * Adds multiple {@link ContextProvider}s. Will be composed into a {@link DefaultContextManager}.
         * Mutually exclusive with {@link #contextManager(ContextManager)}.
         */
        public ContextAwareRetrievalAugmentorBuilder contextProviders(List<ContextProvider> contextProviders) {
            this.contextProviders.addAll(contextProviders);
            return this;
        }

        /**
         * Sets a delegate {@link RetrievalAugmentor} for retrieval (RAG).
         * When set, both context and retrieved content will augment the message.
         * When not set, only context augments the message (pure CAG).
         */
        public ContextAwareRetrievalAugmentorBuilder retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
            this.delegate = retrievalAugmentor;
            return this;
        }

        /**
         * Convenience: sets a single {@link ContentRetriever} as the retrieval delegate.
         * Creates a {@link DefaultRetrievalAugmentor} wrapping this retriever.
         */
        public ContextAwareRetrievalAugmentorBuilder contentRetriever(ContentRetriever contentRetriever) {
            this.delegate = DefaultRetrievalAugmentor.builder()
                    .contentRetriever(ensureNotNull(contentRetriever, "contentRetriever"))
                    .build();
            return this;
        }

        /**
         * Sets the {@link ContentInjector} for injecting context and retrieved content
         * into the message. Defaults to {@link DefaultContentInjector}.
         */
        public ContextAwareRetrievalAugmentorBuilder contentInjector(ContentInjector contentInjector) {
            this.contentInjector = contentInjector;
            return this;
        }

        public ContextAwareRetrievalAugmentor build() {
            ContextManager manager = this.contextManager;
            if (manager == null) {
                DefaultContextManager.DefaultContextManagerBuilder cmBuilder = DefaultContextManager.builder();
                for (ContextProvider provider : contextProviders) {
                    cmBuilder.contextProvider(provider);
                }
                manager = cmBuilder.build();
            }
            return new ContextAwareRetrievalAugmentor(manager, delegate, contentInjector);
        }
    }
}
