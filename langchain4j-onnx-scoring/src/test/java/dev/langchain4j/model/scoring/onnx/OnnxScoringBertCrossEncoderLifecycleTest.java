package dev.langchain4j.model.scoring.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class OnnxScoringBertCrossEncoderLifecycleTest {

    private OrtEnvironment environment;
    private OrtSession session;
    private OrtSession.SessionOptions options;
    private HuggingFaceTokenizer tokenizer;
    private MockedStatic<OrtEnvironment> environments;
    private MockedStatic<HuggingFaceTokenizer> tokenizers;

    @BeforeEach
    void setUp() throws Exception {
        environment = mock(OrtEnvironment.class);
        session = mock(OrtSession.class);
        options = mock(OrtSession.SessionOptions.class);
        tokenizer = mock(HuggingFaceTokenizer.class);
        environments = mockStatic(OrtEnvironment.class);
        tokenizers = mockStatic(HuggingFaceTokenizer.class);

        environments.when(OrtEnvironment::getEnvironment).thenReturn(environment);
        when(environment.createSession("model.onnx", options)).thenReturn(session);
        when(session.getInputNames()).thenReturn(Set.of("input_ids", "attention_mask"));
        tokenizers
                .when(() -> HuggingFaceTokenizer.newInstance(eq(Path.of("tokenizer.json")), anyMap()))
                .thenReturn(tokenizer);
    }

    @AfterEach
    void closeFactories() {
        tokenizers.close();
        environments.close();
    }

    @Test
    void should_close_session_when_tokenizer_loading_fails() throws Exception {
        IOException failure = new IOException("Invalid tokenizer JSON");
        failTokenizerLoading(failure);

        assertThatThrownBy(this::createEncoder)
                .isInstanceOf(RuntimeException.class)
                .hasCause(failure);

        verify(session).close();
        verify(options).close();
        verifyNoInteractions(tokenizer);
    }

    @Test
    void should_close_session_and_preserve_tokenizer_native_loading_error() throws Exception {
        UnsatisfiedLinkError failure = new UnsatisfiedLinkError("Tokenizer native library could not be loaded");
        failTokenizerLoading(failure);

        assertThatThrownBy(this::createEncoder).isSameAs(failure);

        verify(session).close();
        verify(options).close();
    }

    @Test
    void should_preserve_initialization_failure_when_cleanup_also_fails() throws Exception {
        IOException failure = new IOException("Invalid tokenizer JSON");
        OrtException closeFailure = new OrtException("Session cleanup failed");
        failTokenizerLoading(failure);
        doThrow(closeFailure).when(session).close();

        assertThatThrownBy(this::createEncoder).hasCause(failure);

        assertThat(failure.getSuppressed()).containsExactly(closeFailure);
        verify(session).close();
        verify(options).close();
    }

    @Test
    void should_close_both_resources_when_closing_options_fails() throws Exception {
        IllegalStateException failure = new IllegalStateException("Options cleanup failed");
        IllegalStateException tokenizerCloseFailure = new IllegalStateException("Tokenizer cleanup failed");
        doThrow(failure).when(options).close();
        doThrow(tokenizerCloseFailure).when(tokenizer).close();

        assertThatThrownBy(this::createEncoder).hasCause(failure);

        verify(tokenizer).close();
        verify(session).close();
        assertThat(failure.getSuppressed()).containsExactly(tokenizerCloseFailure);
    }

    @Test
    void should_close_options_when_session_creation_fails() throws Exception {
        OrtException failure = new OrtException("Invalid ONNX model");
        when(environment.createSession("model.onnx", options)).thenThrow(failure);

        assertThatThrownBy(this::createEncoder).hasCause(failure);

        verify(options).close();
        verifyNoInteractions(session, tokenizer);
    }

    @Test
    void should_keep_resources_open_until_successful_encoder_is_closed() throws Exception {
        OnnxScoringBertCrossEncoder encoder = createEncoder();

        verify(options).close();
        verify(session, never()).close();
        verify(tokenizer, never()).close();

        encoder.close();
        encoder.close();

        verify(session).close();
        verify(tokenizer).close();
        verify(environment, never()).close();
    }

    private void failTokenizerLoading(Throwable failure) {
        tokenizers
                .when(() -> HuggingFaceTokenizer.newInstance(eq(Path.of("tokenizer.json")), anyMap()))
                .thenThrow(failure);
    }

    private OnnxScoringBertCrossEncoder createEncoder() {
        return new OnnxScoringBertCrossEncoder("model.onnx", options, "tokenizer.json", 512, false);
    }
}
