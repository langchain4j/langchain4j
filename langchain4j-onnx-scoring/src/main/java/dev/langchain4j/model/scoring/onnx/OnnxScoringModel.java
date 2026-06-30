package dev.langchain4j.model.scoring.onnx;

import ai.onnxruntime.OrtSession;

public class OnnxScoringModel extends AbstractInProcessScoringModel {

    private static final int DEFAULT_MODEL_MAX_LENGTH = 510; // 512 - 2 (special tokens [CLS] and [SEP])

    private static final boolean DEFAULT_NORMALIZE = false;

    private final OnnxScoringBertCrossEncoder onnxBertBiEncoder;

    public OnnxScoringModel(String pathToModel, String pathToTokenizer) {
        this.onnxBertBiEncoder = loadFromFileSystem(
                pathToModel, newDefaultSessionOptions(), pathToTokenizer, DEFAULT_MODEL_MAX_LENGTH, DEFAULT_NORMALIZE);
    }

    public OnnxScoringModel(String pathToModel, OrtSession.SessionOptions options, String pathToTokenizer) {
        this.onnxBertBiEncoder =
                loadFromFileSystem(pathToModel, options, pathToTokenizer, DEFAULT_MODEL_MAX_LENGTH, DEFAULT_NORMALIZE);
    }

    public OnnxScoringModel(String pathToModel, String pathToTokenizer, int modelMaxLength) {
        this.onnxBertBiEncoder = loadFromFileSystem(
                pathToModel, newDefaultSessionOptions(), pathToTokenizer, modelMaxLength, DEFAULT_NORMALIZE);
    }

    public OnnxScoringModel(
            String pathToModel,
            OrtSession.SessionOptions options,
            String pathToTokenizer,
            int modelMaxLength,
            boolean normalize) {
        this.onnxBertBiEncoder = loadFromFileSystem(pathToModel, options, pathToTokenizer, modelMaxLength, normalize);
    }

    protected OnnxScoringBertCrossEncoder model() {
        return this.onnxBertBiEncoder;
    }

    private static OrtSession.SessionOptions newDefaultSessionOptions() {
        try {
            return new OrtSession.SessionOptions();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            throw wrapNativeLibraryLoadFailure(e);
        }
    }

    static RuntimeException wrapNativeLibraryLoadFailure(Throwable cause) {
        return new RuntimeException(
                "Failed to initialize ONNX Runtime native library. "
                        + "On Windows, install the latest Microsoft Visual C++ Redistributable for Visual Studio 2015-2022 "
                        + "(see https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist). "
                        + "Also ensure your JVM architecture (x64/ARM64) matches the ONNX Runtime native binary, "
                        + "and that no security software is blocking DLL loading from the temp directory. "
                        + "See https://onnxruntime.ai/docs/install/ for the full list of requirements.",
                cause);
    }
}
