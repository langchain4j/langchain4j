package dev.langchain4j.test.service.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.moderation.listener.ModerationModelErrorContext;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import dev.langchain4j.model.moderation.listener.ModerationModelResponseContext;
import dev.langchain4j.model.scoring.listener.ScoringModelErrorContext;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Passes the model calls made for a captured AI Service invocation (see the listener contexts'
 * {@code invocationId()}) to the {@link InvocationRegistry}. Never throws: an error here must not fail the call.
 */
@Internal
final class ModelCallRecorder
        implements ChatModelListener,
                EmbeddingModelListener,
                ModerationModelListener,
                ScoringModelListener,
                DecisionModelListener {

    private static final Logger LOG = LoggerFactory.getLogger(ModelCallRecorder.class);

    private static final Object CALL = new Object();

    @Override
    public void onRequest(ChatModelRequestContext context) {
        started(context.invocationId(), context.modelProvider(), context.chatRequest(),
                context.attributes());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        ended(context.attributes(), context.chatResponse(), null);
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        ended(context.attributes(), null, context.error());
    }

    @Override
    public void onRequest(EmbeddingModelRequestContext context) {
        started(context.invocationId(), context.modelProvider(),
                context.embeddingRequest(), context.attributes());
    }

    @Override
    public void onResponse(EmbeddingModelResponseContext context) {
        ended(context.attributes(), context.embeddingResponse(), null);
    }

    @Override
    public void onError(EmbeddingModelErrorContext context) {
        ended(context.attributes(), null, context.error());
    }

    @Override
    public void onRequest(ModerationModelRequestContext context) {
        started(context.invocationId(), context.modelProvider(),
                context.moderationRequest(), context.attributes());
    }

    @Override
    public void onResponse(ModerationModelResponseContext context) {
        ended(context.attributes(), context.moderationResponse(), null);
    }

    @Override
    public void onError(ModerationModelErrorContext context) {
        ended(context.attributes(), null, context.error());
    }

    @Override
    public void onRequest(ScoringModelRequestContext context) {
        started(context.invocationId(), context.modelProvider(),
                context.scoringRequest(), context.attributes());
    }

    @Override
    public void onResponse(ScoringModelResponseContext context) {
        ended(context.attributes(), context.scoringResponse(), null);
    }

    @Override
    public void onError(ScoringModelErrorContext context) {
        ended(context.attributes(), null, context.error());
    }

    @Override
    public void onRequest(DecisionModelRequestContext context) {
        started(context.invocationId(), context.modelProvider(),
                context.decisionRequest(), context.attributes());
    }

    @Override
    public void onResponse(DecisionModelResponseContext context) {
        ended(context.attributes(), context.decisionResponse(), null);
    }

    @Override
    public void onError(DecisionModelErrorContext context) {
        ended(context.attributes(), null, context.error());
    }

    private static void started(
            UUID invocationId,
            ModelProvider modelProvider,
            Object request,
            Map<Object, Object> attributes) {
        try {
            ModelCallRecord call = InvocationRegistry.onModelCall(invocationId, modelProvider, request);
            if (call != null) {
                attributes.put(CALL, call);
            }
        } catch (Throwable t) {
            LOG.warn("Failed to capture a model call with a {}", request == null ? null : request.getClass().getSimpleName(), t);
        }
    }

    private static void ended(Map<Object, Object> attributes, Object response, Throwable error) {
        try {
            if (attributes.get(CALL) instanceof ModelCallRecord call) {
                call.end(response, error);
            }
        } catch (Throwable t) {
            LOG.warn("Failed to capture the end of a model call", t);
        }
    }
}
