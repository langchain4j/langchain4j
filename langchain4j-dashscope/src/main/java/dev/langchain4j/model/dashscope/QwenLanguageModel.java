package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.models.QwenParam;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.util.List;

import static com.alibaba.dashscope.aigc.generation.models.QwenParam.ResultFormat.MESSAGE;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.dashscope.QwenHelper.*;
import static dev.langchain4j.model.dashscope.QwenModelName.QWEN_PLUS;

public class QwenLanguageModel implements LanguageModel {
    private final String apiKey;
    private final String modelName;
    private final Double topP;
    private final Integer topK;
    private final Boolean enableSearch;
    private final Integer seed;
    private final Float repetitionPenalty;
    private final Float temperature;
    private final List<String> stops;
    private final Generation generation;

    @Builder
    public QwenLanguageModel(String apiKey,
                             String modelName,
                             Double topP,
                             Integer topK,
                             Boolean enableSearch,
                             Integer seed,
                             Float repetitionPenalty,
                             Float temperature,
                             List<String> stops) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }
        this.modelName = isNullOrBlank(modelName) ? QWEN_PLUS : modelName;
        this.enableSearch = enableSearch != null && enableSearch;
        this.apiKey = apiKey;
        this.topP = topP;
        this.topK = topK;
        this.seed = seed;
        this.repetitionPenalty = repetitionPenalty;
        this.temperature = temperature;
        this.stops = stops;
        this.generation = new Generation();
    }

    @Override
    public Response<String> generate(String prompt) {
        try {
            QwenParam.QwenParamBuilder<?, ?> builder = QwenParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .topP(topP)
                    .topK(topK)
                    .enableSearch(enableSearch)
                    .seed(seed)
                    .repetitionPenalty(repetitionPenalty)
                    .temperature(temperature)
                    .prompt(prompt)
                    .resultFormat(MESSAGE);

            if (stops != null) {
                builder.stopStrings(stops);
            }

            GenerationResult generationResult = generation.call(builder.build());

            return Response.from(answerFrom(generationResult),
                    tokenUsageFrom(generationResult), finishReasonFrom(generationResult));
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }
}
