package dev.langchain4j.model.moderation.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelErrorContext;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import dev.langchain4j.model.moderation.listener.ModerationModelResponseContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Make sure these dependencies are present in the module where this test class is extended:
 * <pre>
 *
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <classifier>tests</classifier>
 *     <type>test-jar</type>
 *     <scope>test</scope>
 * </dependency>
 *
 * </pre>
 */
public abstract class AbstractModerationModelListenerIT {

    protected abstract ModerationModel createModel(List<ModerationModelListener> listeners);

    protected ModerationModel createModel(ModerationModelListener listener) {
        return createModel(List.of(listener));
    }

    protected abstract ModerationModel createFailingModel(List<ModerationModelListener> listeners);

    protected ModerationModel createFailingModel(ModerationModelListener listener) {
        return createFailingModel(List.of(listener));
    }

    protected abstract Class<? extends Exception> expectedExceptionClass();

    @Test
    void should_listen_request_and_response() {

        // given
        AtomicReference<ModerationRequest> moderationRequestReference = new AtomicReference<>();
        AtomicInteger onRequestInvocations = new AtomicInteger();

        AtomicReference<ModerationResponse> moderationResponseReference = new AtomicReference<>();
        AtomicInteger onResponseInvocations = new AtomicInteger();
        AtomicReference<ModerationModel> modelReference = new AtomicReference<>();

        ModerationModelListener listener = new ModerationModelListener() {

            @Override
            public void onRequest(ModerationModelRequestContext requestContext) {
                moderationRequestReference.set(requestContext.moderationRequest());
                onRequestInvocations.incrementAndGet();

                assertThat(requestContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                assertThat(requestContext.modelName())
                        .isEqualTo(modelReference.get().modelName());

                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ModerationModelResponseContext responseContext) {
                moderationResponseReference.set(responseContext.moderationResponse());
                onResponseInvocations.incrementAndGet();

                assertThat(responseContext.moderationRequest()).isEqualTo(moderationRequestReference.get());

                assertThat(responseContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                assertThat(responseContext.modelName())
                        .isEqualTo(modelReference.get().modelName());

                assertThat(responseContext.attributes()).containsEntry("id", "12345");
            }

            @Override
            public void onError(ModerationModelErrorContext errorContext) {
                fail("onError() must not be called. Exception: "
                        + errorContext.error().getMessage());
            }
        };

        ModerationModel model = createModel(listener);
        modelReference.set(model);

        String textToModerate = "hello";
        ModerationRequest moderationRequest =
                ModerationRequest.builder().text(textToModerate).build();

        // when
        ModerationResponse moderationResponse = model.moderate(moderationRequest);

        // then
        ModerationRequest observedModerationRequest = moderationRequestReference.get();
        assertThat(observedModerationRequest.text()).isEqualTo(textToModerate);

        assertThat(onRequestInvocations).hasValue(1);

        ModerationResponse observedResponse = moderationResponseReference.get();
        assertThat(observedResponse).isNotNull();
        assertThat(observedResponse.moderation()).isEqualTo(moderationResponse.moderation());

        assertThat(onResponseInvocations).hasValue(1);
    }

    @Test
    void should_listen_error() {

        // given
        AtomicReference<ModerationRequest> moderationRequestReference = new AtomicReference<>();
        AtomicInteger onRequestInvocations = new AtomicInteger();

        AtomicReference<Throwable> errorReference = new AtomicReference<>();
        AtomicInteger onErrorInvocations = new AtomicInteger();
        AtomicReference<ModerationModel> modelReference = new AtomicReference<>();

        ModerationModelListener listener = new ModerationModelListener() {

            @Override
            public void onRequest(ModerationModelRequestContext requestContext) {
                moderationRequestReference.set(requestContext.moderationRequest());
                onRequestInvocations.incrementAndGet();

                assertThat(requestContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                assertThat(requestContext.modelName())
                        .isEqualTo(modelReference.get().modelName());

                requestContext.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(ModerationModelResponseContext responseContext) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(ModerationModelErrorContext errorContext) {
                errorReference.set(errorContext.error());
                onErrorInvocations.incrementAndGet();

                assertThat(errorContext.moderationRequest()).isEqualTo(moderationRequestReference.get());

                assertThat(errorContext.modelProvider())
                        .isNotNull()
                        .isEqualTo(modelReference.get().provider());

                assertThat(errorContext.modelName())
                        .isEqualTo(modelReference.get().modelName());

                assertThat(errorContext.attributes()).containsEntry("id", "12345");
            }
        };

        ModerationModel model = createFailingModel(listener);
        modelReference.set(model);

        String textToModerate = "this message will fail";

        ModerationRequest moderationRequest =
                ModerationRequest.builder().text(textToModerate).build();

        // when
        Throwable thrown = catchThrowable(() -> model.moderate(moderationRequest));

        // then
        Throwable error = errorReference.get();
        assertThat(error).isExactlyInstanceOf(expectedExceptionClass());

        assertThat(thrown).isNotNull();
        assertThat(error).isIn(thrown, thrown.getCause());

        assertThat(onRequestInvocations).hasValue(1);
        assertThat(onErrorInvocations).hasValue(1);
    }

    @Test
    void should_continue_executing_other_listeners_when_one_throws_exception() {

        // given
        AtomicInteger firstListenerOnRequestInvocations = new AtomicInteger();
        AtomicInteger firstListenerOnResponseInvocations = new AtomicInteger();

        AtomicInteger secondListenerOnRequestInvocations = new AtomicInteger();
        AtomicInteger secondListenerOnResponseInvocations = new AtomicInteger();

        ModerationModelListener failingListener = new ModerationModelListener() {

            @Override
            public void onRequest(ModerationModelRequestContext requestContext) {
                firstListenerOnRequestInvocations.incrementAndGet();
                throw new RuntimeException("Simulated failure in onRequest");
            }

            @Override
            public void onResponse(ModerationModelResponseContext responseContext) {
                firstListenerOnResponseInvocations.incrementAndGet();
                throw new RuntimeException("Simulated failure in onResponse");
            }

            @Override
            public void onError(ModerationModelErrorContext errorContext) {
                throw new RuntimeException("Simulated failure in onError");
            }
        };

        ModerationModelListener successfulListener = new ModerationModelListener() {

            @Override
            public void onRequest(ModerationModelRequestContext requestContext) {
                secondListenerOnRequestInvocations.incrementAndGet();
            }

            @Override
            public void onResponse(ModerationModelResponseContext responseContext) {
                secondListenerOnResponseInvocations.incrementAndGet();
            }
        };

        ModerationModel model = createModel(List.of(failingListener, successfulListener));

        String textToModerate = "hello";
        ModerationRequest moderationRequest =
                ModerationRequest.builder().text(textToModerate).build();

        // when
        ModerationResponse response = model.moderate(moderationRequest);

        // then - both listeners should have been invoked despite the first one throwing exceptions
        assertThat(response).isNotNull();
        assertThat(response.moderation()).isNotNull();

        assertThat(firstListenerOnRequestInvocations).hasValue(1);
        assertThat(firstListenerOnResponseInvocations).hasValue(1);

        assertThat(secondListenerOnRequestInvocations).hasValue(1);
        assertThat(secondListenerOnResponseInvocations).hasValue(1);
    }

    @Test
    void should_continue_executing_other_listeners_when_one_throws_exception_in_onError() {

        // given
        AtomicInteger firstListenerOnErrorInvocations = new AtomicInteger();
        AtomicInteger secondListenerOnErrorInvocations = new AtomicInteger();

        ModerationModelListener failingListener = new ModerationModelListener() {

            @Override
            public void onError(ModerationModelErrorContext errorContext) {
                firstListenerOnErrorInvocations.incrementAndGet();
                throw new RuntimeException("Simulated failure in onError");
            }
        };

        ModerationModelListener successfulListener = new ModerationModelListener() {

            @Override
            public void onError(ModerationModelErrorContext errorContext) {
                secondListenerOnErrorInvocations.incrementAndGet();
            }
        };

        ModerationModel model = createFailingModel(List.of(failingListener, successfulListener));

        ModerationRequest moderationRequest =
                ModerationRequest.builder().text("this message will fail").build();

        // when
        try {
            model.moderate(moderationRequest);
        } catch (Exception e) {
            // expected
        }

        // then - both listeners should have been invoked despite the first one throwing an exception
        assertThat(firstListenerOnErrorInvocations).hasValue(1);
        assertThat(secondListenerOnErrorInvocations).hasValue(1);
    }
}
