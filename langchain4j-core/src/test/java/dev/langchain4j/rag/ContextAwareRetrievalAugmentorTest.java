package dev.langchain4j.rag;

import dev.langchain4j.context.ContextManager;
import dev.langchain4j.context.ContextProvider;
import dev.langchain4j.context.ContextRequest;
import dev.langchain4j.context.ContextResult;
import dev.langchain4j.context.InvocationParameterContextProvider;
import dev.langchain4j.context.StaticContextProvider;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ContextAwareRetrievalAugmentorTest {

    // -- Scenario 1: Pure CAG -- Customer support with pre-loaded policies --

    @Test
    void should_augment_with_static_context_only_without_retrieval() {

        // given
        ContextProvider policies = StaticContextProvider.of(
                "Return policy: Full refund within 30 days of purchase.",
                "Shipping: Free standard shipping on orders over $50.");

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(policies)
                .build();

        UserMessage userMessage = UserMessage.from("Can I return my order?");
        Metadata metadata = createMetadata(userMessage);

        // when
        AugmentationResult result = augmentor.augment(new AugmentationRequest(userMessage, metadata));

        // then
        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents().get(0).textSegment().text())
                .isEqualTo("Return policy: Full refund within 30 days of purchase.");
        assertThat(result.contents().get(1).textSegment().text())
                .isEqualTo("Shipping: Free standard shipping on orders over $50.");

        UserMessage augmented = (UserMessage) result.chatMessage();
        assertThat(augmented.singleText())
                .contains("Can I return my order?")
                .contains("Return policy: Full refund within 30 days of purchase.")
                .contains("Shipping: Free standard shipping on orders over $50.");
    }

    // -- Scenario 2: Hybrid CAG+RAG -- Role-based enterprise assistant --

    @Test
    void should_merge_context_and_retrieved_content_in_order() {

        // given
        ContextProvider compliance = StaticContextProvider.of("All responses must comply with GDPR.");
        ContextProvider userProfile = InvocationParameterContextProvider.of("userProfile");

        Content retrievedDoc = Content.from("Q4 revenue was $2.3M across all regions.");
        ContentRetriever retriever = spy(new TestContentRetriever(retrievedDoc));

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(compliance)
                .contextProvider(userProfile)
                .contentRetriever(retriever)
                .build();

        InvocationParameters params = InvocationParameters.from(
                "userProfile", "User: Jane Doe, Role: Manager, Department: Sales");
        UserMessage userMessage = UserMessage.from("Show me Q4 revenue data");
        Metadata metadata = createMetadata(userMessage, params);

        // when
        AugmentationResult result = augmentor.augment(new AugmentationRequest(userMessage, metadata));

        // then -- context first, then retrieved
        assertThat(result.contents()).hasSize(3);
        assertThat(result.contents().get(0).textSegment().text())
                .isEqualTo("All responses must comply with GDPR.");
        assertThat(result.contents().get(1).textSegment().text())
                .isEqualTo("User: Jane Doe, Role: Manager, Department: Sales");
        assertThat(result.contents().get(2).textSegment().text())
                .isEqualTo("Q4 revenue was $2.3M across all regions.");

        verify(retriever).retrieve(any());

        UserMessage augmented = (UserMessage) result.chatMessage();
        assertThat(augmented.singleText())
                .contains("Show me Q4 revenue data")
                .contains("GDPR")
                .contains("Jane Doe")
                .contains("$2.3M");
    }

    // -- Scenario 3: Context-gated retrieval -- FAQ knowledge base --

    @Test
    void should_skip_retrieval_when_context_result_advises_against_it() {

        // given -- custom ContextManager that gates retrieval
        ContextManager faqManager = request -> {
            String question = ((UserMessage) request.chatMessage()).singleText();
            if (question.contains("return")) {
                return ContextResult.withoutRetrieval(List.of(
                        Content.from("To return an item, visit our returns portal.")));
            }
            return ContextResult.from(List.of());
        };

        ContentRetriever retriever = spy(new TestContentRetriever(Content.from("fallback doc")));

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextManager(faqManager)
                .contentRetriever(retriever)
                .build();

        // when -- question matches FAQ
        UserMessage faqQuestion = UserMessage.from("How do I return an item?");
        AugmentationResult result1 = augmentor.augment(
                new AugmentationRequest(faqQuestion, createMetadata(faqQuestion)));

        // then -- retrieval skipped, FAQ answer injected
        verify(retriever, never()).retrieve(any());
        assertThat(result1.contents()).hasSize(1);
        assertThat(result1.contents().get(0).textSegment().text())
                .isEqualTo("To return an item, visit our returns portal.");

        // when -- question doesn't match FAQ
        UserMessage otherQuestion = UserMessage.from("What are your store hours?");
        AugmentationResult result2 = augmentor.augment(
                new AugmentationRequest(otherQuestion, createMetadata(otherQuestion)));

        // then -- retrieval performed
        verify(retriever).retrieve(any());
        assertThat(result2.contents()).hasSize(1);
        assertThat(result2.contents().get(0).textSegment().text())
                .isEqualTo("fallback doc");
    }

    // -- Scenario 4: Context propagation to downstream RAG components --

    @Test
    void should_propagate_context_to_downstream_rag_components_via_invocation_parameters() {

        // given
        ContextProvider departmentContext = request -> {
            String dept = request.invocationParameters().get("department");
            return List.of(Content.from("Department: " + dept));
        };

        ContentRetriever hrRetriever = spy(new TestContentRetriever(Content.from("HR doc")));
        ContentRetriever engRetriever = spy(new TestContentRetriever(Content.from("Eng doc")));

        QueryRouter contextAwareRouter = query -> {
            List<Content> ctx = query.metadata().invocationParameters()
                    .get(ContextAwareRetrievalAugmentor.CONTEXT_KEY);
            boolean isHR = ctx != null && ctx.stream()
                    .anyMatch(c -> c.textSegment().text().contains("Department: HR"));
            return singletonList(isHR ? hrRetriever : engRetriever);
        };

        RetrievalAugmentor ragDelegate = DefaultRetrievalAugmentor.builder()
                .queryRouter(contextAwareRouter)
                .build();

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(departmentContext)
                .retrievalAugmentor(ragDelegate)
                .build();

        // when -- HR user
        InvocationParameters hrParams = InvocationParameters.from("department", "HR");
        UserMessage userMessage = UserMessage.from("Show me policies");
        augmentor.augment(new AugmentationRequest(userMessage, createMetadata(userMessage, hrParams)));

        // then -- routed to HR retriever
        verify(hrRetriever).retrieve(any());
        verify(engRetriever, never()).retrieve(any());
    }

    // -- Scenario 5: ContentRetriever reading resolved context via CONTEXT_KEY --

    @Test
    void should_allow_content_retriever_to_filter_results_using_resolved_context() {

        // given -- context resolves the user's clearance level
        ContextProvider clearanceProvider = request -> {
            String level = request.invocationParameters().get("clearanceLevel");
            return List.of(Content.from("Clearance: " + level));
        };

        // A retriever that filters documents based on the resolved context
        Content publicDoc = Content.from("Project roadmap (public)");
        Content classifiedDoc = Content.from("Budget forecast (classified)");

        ContentRetriever securityAwareRetriever = query -> {
            List<Content> ctx = query.metadata().invocationParameters()
                    .get(ContextAwareRetrievalAugmentor.CONTEXT_KEY);
            boolean hasClassifiedAccess = ctx != null && ctx.stream()
                    .anyMatch(c -> c.textSegment().text().contains("Clearance: TOP_SECRET"));
            if (hasClassifiedAccess) {
                return List.of(publicDoc, classifiedDoc);
            }
            return List.of(publicDoc);
        };

        RetrievalAugmentor ragDelegate = DefaultRetrievalAugmentor.builder()
                .contentRetriever(securityAwareRetriever)
                .build();

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(clearanceProvider)
                .retrievalAugmentor(ragDelegate)
                .build();

        // when -- user with TOP_SECRET clearance
        InvocationParameters topSecretParams = InvocationParameters.from("clearanceLevel", "TOP_SECRET");
        UserMessage userMessage = UserMessage.from("Show me project docs");
        AugmentationResult classifiedResult = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage, topSecretParams)));

        // then -- gets both public and classified documents
        assertThat(classifiedResult.contents()).hasSize(3); // 1 context + 2 retrieved
        assertThat(classifiedResult.contents().get(1).textSegment().text()).isEqualTo("Project roadmap (public)");
        assertThat(classifiedResult.contents().get(2).textSegment().text()).isEqualTo("Budget forecast (classified)");

        // when -- user with PUBLIC clearance
        InvocationParameters publicParams = InvocationParameters.from("clearanceLevel", "PUBLIC");
        AugmentationResult publicResult = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage, publicParams)));

        // then -- gets only public documents
        assertThat(publicResult.contents()).hasSize(2); // 1 context + 1 retrieved
        assertThat(publicResult.contents().get(1).textSegment().text()).isEqualTo("Project roadmap (public)");
    }

    // -- Scenario 6: Graceful degradation with failing provider --

    @Test
    void should_continue_when_a_context_provider_fails() {

        // given
        ContextProvider failingProvider = request -> {
            throw new RuntimeException("DB unavailable");
        };
        ContextProvider workingProvider = StaticContextProvider.of("Working context.");
        ContentRetriever retriever = new TestContentRetriever(Content.from("Retrieved doc."));

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(failingProvider)
                .contextProvider(workingProvider)
                .contentRetriever(retriever)
                .build();

        UserMessage userMessage = UserMessage.from("test");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- no exception, working context + retrieved doc present
        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("Working context.");
        assertThat(result.contents().get(1).textSegment().text()).isEqualTo("Retrieved doc.");
    }

    // -- Scenario 7: Wrapping an existing DefaultRetrievalAugmentor --

    @Test
    void should_work_as_wrapper_around_existing_retrieval_augmentor() {

        // given -- existing advanced RAG setup
        QueryTransformer queryTransformer = spy(new DefaultQueryTransformer());
        ContentRetriever retriever = spy(new TestContentRetriever(Content.from("doc from existing RAG")));
        ContentAggregator aggregator = spy(new TestContentAggregator());

        DefaultRetrievalAugmentor existingRag = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(retriever)
                .contentAggregator(aggregator)
                .build();

        // Layer context on top
        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(StaticContextProvider.of("Always respond in formal English."))
                .retrievalAugmentor(existingRag)
                .build();

        UserMessage userMessage = UserMessage.from("test");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- existing RAG pipeline components invoked
        verify(queryTransformer).transform(any());
        verify(retriever).retrieve(any());
        verify(aggregator).aggregate(any());

        // result includes both context and retrieved content
        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents().get(0).textSegment().text())
                .isEqualTo("Always respond in formal English.");
        assertThat(result.contents().get(1).textSegment().text())
                .isEqualTo("doc from existing RAG");
    }

    // -- Scenario 8: Multiple context providers with custom ContentInjector --

    @Test
    void should_use_custom_content_injector_with_multiple_providers() {

        // given
        ContextProvider policies = StaticContextProvider.of("No medical advice.");
        ContextProvider userCtx = StaticContextProvider.of("User is a premium subscriber.");
        ContentRetriever retriever = new TestContentRetriever(Content.from("Article about headaches."));

        ContentInjector customInjector = DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from("{{userMessage}}\n\nContext:\n{{contents}}"))
                .build();

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(policies)
                .contextProvider(userCtx)
                .contentRetriever(retriever)
                .contentInjector(customInjector)
                .build();

        UserMessage userMessage = UserMessage.from("I have a headache");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- custom template used
        assertThat(result.contents()).hasSize(3);
        UserMessage augmented = (UserMessage) result.chatMessage();
        assertThat(augmented.singleText())
                .startsWith("I have a headache\n\nContext:\n")
                .contains("No medical advice.")
                .contains("User is a premium subscriber.")
                .contains("Article about headaches.");
    }

    // -- Scenario 9: Empty context -- pure retrieval fallback --

    @Test
    void should_fall_through_to_pure_retrieval_when_context_is_empty() {

        // given
        ContextProvider emptyProvider = request -> List.of();
        ContentRetriever retriever = new TestContentRetriever(Content.from("retrieved content"));

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(emptyProvider)
                .contentRetriever(retriever)
                .build();

        UserMessage userMessage = UserMessage.from("test");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- only retrieved content, no context
        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("retrieved content");
    }

    // -- Additional unit-level tests --

    @Test
    void should_return_original_message_when_no_context_and_no_delegate() {

        // given
        ContextProvider emptyProvider = request -> List.of();

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(emptyProvider)
                .build();

        UserMessage userMessage = UserMessage.from("hello");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- message unchanged, no contents
        assertThat(result.contents()).isEmpty();
        assertThat(((UserMessage) result.chatMessage()).singleText()).isEqualTo("hello");
    }

    @Test
    void should_store_context_in_invocation_parameters() {

        // given
        ContextProvider provider = StaticContextProvider.of("context data");
        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(provider)
                .build();

        InvocationParameters params = new InvocationParameters();
        UserMessage userMessage = UserMessage.from("test");
        Metadata metadata = createMetadata(userMessage, params);

        // when
        augmentor.augment(new AugmentationRequest(userMessage, metadata));

        // then
        List<Content> storedContext = params.get(ContextAwareRetrievalAugmentor.CONTEXT_KEY);
        assertThat(storedContext).hasSize(1);
        assertThat(storedContext.get(0).textSegment().text()).isEqualTo("context data");
    }

    // -- ContextProvider.from(ContentRetriever) bridge tests --

    @Test
    void should_use_content_retriever_as_context_provider() {

        // given
        ContentRetriever retriever = spy(new TestContentRetriever(
                Content.from("FAQ: To return an item, visit our returns portal.")));

        ContextProvider faqContext = ContextProvider.from(retriever);

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(faqContext)
                .build();

        UserMessage userMessage = UserMessage.from("How do I return an item?");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- retriever was called and its content is in the result
        verify(retriever).retrieve(any());
        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).textSegment().text())
                .isEqualTo("FAQ: To return an item, visit our returns portal.");
    }

    @Test
    void should_pass_query_text_and_metadata_to_wrapped_retriever() {

        // given -- a retriever that captures the query it receives
        ContentRetriever capturingRetriever = spy(new TestContentRetriever(Content.from("doc")));

        ContextProvider bridged = ContextProvider.from(capturingRetriever);

        InvocationParameters params = InvocationParameters.from("key", "value");
        UserMessage userMessage = UserMessage.from("specific question");
        Metadata metadata = createMetadata(userMessage, params);

        // when
        ContextRequest contextRequest = new ContextRequest(userMessage, metadata);
        List<Content> contents = bridged.provideContext(contextRequest);

        // then
        assertThat(contents).hasSize(1);
        verify(capturingRetriever).retrieve(any(Query.class));
    }

    @Test
    void should_combine_static_and_retriever_based_context_providers() {

        // given -- static policies + retriever-backed FAQ, both as context
        ContextProvider policies = StaticContextProvider.of("All responses must comply with GDPR.");
        ContentRetriever faqRetriever = new TestContentRetriever(
                Content.from("Returns are processed within 5 business days."));
        ContextProvider faqContext = ContextProvider.from(faqRetriever);

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(policies)
                .contextProvider(faqContext)
                .build();

        UserMessage userMessage = UserMessage.from("How long do returns take?");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- both static and retriever-based context present
        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents().get(0).textSegment().text())
                .isEqualTo("All responses must comply with GDPR.");
        assertThat(result.contents().get(1).textSegment().text())
                .isEqualTo("Returns are processed within 5 business days.");
    }

    @Test
    void should_have_descriptive_name_for_bridged_provider() {
        ContentRetriever retriever = new TestContentRetriever(Content.from("doc"));
        ContextProvider bridged = ContextProvider.from(retriever);

        assertThat(bridged.name()).isEqualTo("ContentRetrieverContextProvider[TestContentRetriever]");
    }

    // -- Token budget management tests --

    @Test
    void should_trim_retrieved_content_when_exceeding_token_budget() {

        // given -- context uses 10 chars, budget is 25, so only 15 chars left for retrieval
        ContextProvider context = StaticContextProvider.of("0123456789"); // 10 tokens
        ContentRetriever retriever = new TestContentRetriever(
                Content.from("aaaaaaaaaa"), // 10 tokens -- fits (cumulative: 10)
                Content.from("bbbbbbbbbb"), // 10 tokens -- doesn't fit (cumulative: 20 > 15)
                Content.from("cccccccccc")  // 10 tokens -- doesn't fit
        );

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(context)
                .contentRetriever(retriever)
                .maxTokens(25)
                .tokenCountEstimator(charCountEstimator())
                .build();

        UserMessage userMessage = UserMessage.from("test");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- only context + first retrieved item fit
        assertThat(result.contents()).hasSize(2);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("0123456789");
        assertThat(result.contents().get(1).textSegment().text()).isEqualTo("aaaaaaaaaa");
    }

    @Test
    void should_trim_context_and_discard_retrieval_when_context_exceeds_budget() {

        // given -- two context items (10 tokens each), budget is 15, only first fits
        ContextProvider context = StaticContextProvider.of("0123456789", "abcdefghij"); // 10 + 10 = 20 tokens
        ContentRetriever retriever = spy(new TestContentRetriever(Content.from("retrieved")));

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(context)
                .contentRetriever(retriever)
                .maxTokens(15)
                .tokenCountEstimator(charCountEstimator())
                .build();

        UserMessage userMessage = UserMessage.from("test");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- only first context item fits, retrieval has no remaining budget
        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).textSegment().text()).isEqualTo("0123456789");
    }

    @Test
    void should_include_all_content_when_within_token_budget() {

        // given -- context 10 tokens + retrieval 10 tokens = 20, budget is 30
        ContextProvider context = StaticContextProvider.of("0123456789"); // 10 tokens
        ContentRetriever retriever = new TestContentRetriever(
                Content.from("aaaaaaaaaa"),  // 10 tokens
                Content.from("bbbbbbbbbb")); // 10 tokens

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(context)
                .contentRetriever(retriever)
                .maxTokens(30)
                .tokenCountEstimator(charCountEstimator())
                .build();

        UserMessage userMessage = UserMessage.from("test");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- all content included
        assertThat(result.contents()).hasSize(3);
    }

    @Test
    void should_not_trim_when_max_tokens_is_not_set() {

        // given -- no maxTokens configured, large content passes through
        ContextProvider context = StaticContextProvider.of("large context content");
        ContentRetriever retriever = new TestContentRetriever(
                Content.from("doc1"), Content.from("doc2"), Content.from("doc3"));

        ContextAwareRetrievalAugmentor augmentor = ContextAwareRetrievalAugmentor.builder()
                .contextProvider(context)
                .contentRetriever(retriever)
                .build();

        UserMessage userMessage = UserMessage.from("test");

        // when
        AugmentationResult result = augmentor.augment(
                new AugmentationRequest(userMessage, createMetadata(userMessage)));

        // then -- all content included, no trimming
        assertThat(result.contents()).hasSize(4);
    }

    @Test
    void should_reject_max_tokens_without_token_count_estimator() {
        assertThatThrownBy(() -> ContextAwareRetrievalAugmentor.builder()
                .contextProvider(StaticContextProvider.of("test"))
                .maxTokens(100)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenCountEstimator");
    }

    // -- Helpers --

    private static TokenCountEstimator charCountEstimator() {
        return new TokenCountEstimator() {
            @Override
            public int estimateTokenCountInText(String text) {
                return text.length();
            }

            @Override
            public int estimateTokenCountInMessage(ChatMessage message) {
                return 0;
            }

            @Override
            public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
                return 0;
            }
        };
    }

    private static Metadata createMetadata(UserMessage userMessage) {
        return createMetadata(userMessage, new InvocationParameters());
    }

    private static Metadata createMetadata(UserMessage userMessage, InvocationParameters params) {
        return Metadata.builder()
                .chatMessage(userMessage)
                .invocationContext(InvocationContext.builder()
                        .invocationParameters(params)
                        .build())
                .build();
    }

    static class TestContentRetriever implements ContentRetriever {

        private final List<Content> contents;

        TestContentRetriever(Content... contents) {
            this.contents = asList(contents);
        }

        @Override
        public List<Content> retrieve(Query query) {
            return contents;
        }
    }

    static class TestContentAggregator implements ContentAggregator {

        @Override
        public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
            return queryToContents.values().stream()
                    .flatMap(Collection::stream)
                    .flatMap(List::stream)
                    .collect(toList());
        }
    }
}
