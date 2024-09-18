package dev.langchain4j.model.scoring.onnx;

import ai.onnxruntime.OrtSession;

public class OnnxScoringModel extends AbstractInProcessScoringModel {

    private static final int DEFAULT_MODEL_MAX_LENGTH = 510; // 512 - 2 (special tokens [CLS] and [SEP])

    private static final boolean DEFAULT_NORMALIZE = false;

    private final OnnxScoringBertBiEncoder onnxBertBiEncoder;

    public OnnxScoringModel(String pathToModel, String pathToTokenizer) {
        this.onnxBertBiEncoder = loadFromFileSystem(pathToModel, new OrtSession.SessionOptions(), pathToTokenizer, DEFAULT_MODEL_MAX_LENGTH, DEFAULT_NORMALIZE);
    }

    public OnnxScoringModel(String pathToModel, OrtSession.SessionOptions option, String pathToTokenizer) {
        this.onnxBertBiEncoder = loadFromFileSystem(pathToModel, option, pathToTokenizer, DEFAULT_MODEL_MAX_LENGTH, DEFAULT_NORMALIZE);
    }

    public OnnxScoringModel(String pathToModel, String pathToTokenizer, int modelMaxLength) {
        this.onnxBertBiEncoder = loadFromFileSystem(pathToModel, new OrtSession.SessionOptions(), pathToTokenizer, modelMaxLength, DEFAULT_NORMALIZE);
    }

    public OnnxScoringModel(String pathToModel, OrtSession.SessionOptions option, String pathToTokenizer, int modelMaxLength, boolean normalize) {
        this.onnxBertBiEncoder = loadFromFileSystem(pathToModel, option, pathToTokenizer, modelMaxLength, normalize);
    }

    protected OnnxScoringBertBiEncoder model() {
        return this.onnxBertBiEncoder;
    }
}
