package dev.langchain4j.model.scoring.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OnnxScoringModelTest {

    @Test
    void wrapNativeLibraryLoadFailure_should_preserve_cause_and_provide_actionable_message() {
        UnsatisfiedLinkError cause = new UnsatisfiedLinkError(
                "C:\\Users\\Administrator\\AppData\\Local\\Temp\\onnxruntime-java\\onnxruntime.dll: "
                        + "Dynamic Link Library (DLL) initialization routine failed");

        RuntimeException wrapped = OnnxScoringModel.wrapNativeLibraryLoadFailure(cause);

        assertThat(wrapped.getCause()).isSameAs(cause);
        assertThat(wrapped.getMessage()).contains("ONNX Runtime").contains("Visual C++ Redistributable");
    }

    @Test
    void wrapNativeLibraryLoadFailure_should_handle_no_class_def_found_error() {
        NoClassDefFoundError cause = new NoClassDefFoundError("ai/onnxruntime/OrtSession$SessionOptions");

        RuntimeException wrapped = OnnxScoringModel.wrapNativeLibraryLoadFailure(cause);

        assertThat(wrapped.getCause()).isSameAs(cause);
        assertThat(wrapped.getMessage()).contains("ONNX Runtime");
    }
}
