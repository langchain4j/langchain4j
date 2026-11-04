package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GoogleMaps;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Retrieval;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolCodeExecution;
import com.google.genai.types.ToolConfig;
import com.google.genai.types.UrlContext;
import com.google.genai.types.VertexAISearch;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class GoogleGenAiConfigBuilder {

    static GenerateContentConfig buildConfig(
            ChatRequestParameters parameters,
            Content systemInstruction,
            List<SafetySetting> safetySettings,
            Integer thinkingBudget,
            String thinkingLevel,
            Boolean includeThoughts,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            List<String> allowedFunctionNames,
            String vertexSearchDatastore,
            Map<String, String> labels,
            String cachedContent) {
        return buildConfig(
                parameters,
                systemInstruction,
                safetySettings,
                thinkingBudget,
                thinkingLevel,
                includeThoughts,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames,
                vertexSearchDatastore,
                labels,
                cachedContent,
                null,
                null,
                null,
                null,
                null);
    }

    static GenerateContentConfig buildConfig(
            ChatRequestParameters parameters,
            Content systemInstruction,
            List<SafetySetting> safetySettings,
            Integer thinkingBudget,
            String thinkingLevel,
            Boolean includeThoughts,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            List<String> allowedFunctionNames,
            String vertexSearchDatastore,
            Map<String, String> labels,
            String cachedContent,
            Consumer<GenerateContentConfig.Builder> generateContentConfigCustomizer) {
        return buildConfig(
                parameters,
                systemInstruction,
                safetySettings,
                thinkingBudget,
                thinkingLevel,
                includeThoughts,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames,
                vertexSearchDatastore,
                labels,
                cachedContent,
                null,
                null,
                null,
                null,
                generateContentConfigCustomizer);
    }

    static GenerateContentConfig buildConfig(
            ChatRequestParameters parameters,
            Content systemInstruction,
            List<SafetySetting> safetySettings,
            Integer thinkingBudget,
            String thinkingLevel,
            Boolean includeThoughts,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            List<String> allowedFunctionNames,
            String vertexSearchDatastore,
            Map<String, String> labels,
            String cachedContent,
            AudioTranscriptionConfig audioTranscriptionConfig,
            Consumer<GenerateContentConfig.Builder> generateContentConfigCustomizer) {
        return buildConfig(
                parameters,
                systemInstruction,
                safetySettings,
                thinkingBudget,
                thinkingLevel,
                includeThoughts,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames,
                vertexSearchDatastore,
                labels,
                cachedContent,
                audioTranscriptionConfig,
                null,
                null,
                null,
                generateContentConfigCustomizer);
    }

    static GenerateContentConfig buildConfig(
            ChatRequestParameters parameters,
            Content systemInstruction,
            List<SafetySetting> safetySettings,
            Integer thinkingBudget,
            String thinkingLevel,
            Boolean includeThoughts,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            List<String> allowedFunctionNames,
            String vertexSearchDatastore,
            Map<String, String> labels,
            String cachedContent,
            AudioTranscriptionConfig audioTranscriptionConfig,
            SpeechConfig speechConfig,
            List<String> responseModalities,
            Boolean automaticContinuation,
            Consumer<GenerateContentConfig.Builder> generateContentConfigCustomizer) {
        return buildConfig(
                parameters,
                systemInstruction,
                safetySettings,
                thinkingBudget,
                thinkingLevel,
                includeThoughts,
                seed,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                false,
                allowedFunctionNames,
                vertexSearchDatastore,
                labels,
                cachedContent,
                audioTranscriptionConfig,
                speechConfig,
                responseModalities,
                automaticContinuation,
                generateContentConfigCustomizer);
    }

    static GenerateContentConfig buildConfig(
            ChatRequestParameters parameters,
            Content systemInstruction,
            List<SafetySetting> safetySettings,
            Integer thinkingBudget,
            String thinkingLevel,
            Boolean includeThoughts,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            boolean codeExecutionEnabled,
            List<String> allowedFunctionNames,
            String vertexSearchDatastore,
            Map<String, String> labels,
            String cachedContent,
            AudioTranscriptionConfig audioTranscriptionConfig,
            SpeechConfig speechConfig,
            List<String> responseModalities,
            Boolean automaticContinuation,
            Consumer<GenerateContentConfig.Builder> generateContentConfigCustomizer) {

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

        if (parameters.temperature() != null) {
            configBuilder.temperature(parameters.temperature().floatValue());
        }
        if (parameters.topP() != null) {
            configBuilder.topP(parameters.topP().floatValue());
        }
        if (parameters.topK() != null) {
            configBuilder.topK(parameters.topK().floatValue());
        }
        if (parameters.frequencyPenalty() != null) {
            configBuilder.frequencyPenalty(parameters.frequencyPenalty().floatValue());
        }
        if (parameters.presencePenalty() != null) {
            configBuilder.presencePenalty(parameters.presencePenalty().floatValue());
        }
        if (parameters.maxOutputTokens() != null) {
            configBuilder.maxOutputTokens(parameters.maxOutputTokens());
        }
        if (parameters.stopSequences() != null) {
            configBuilder.stopSequences(parameters.stopSequences());
        }

        if (!isNullOrEmpty(safetySettings)) {
            configBuilder.safetySettings(safetySettings);
        }

        ResponseFormat responseFormat = parameters.responseFormat();
        if (responseFormat != null && responseFormat.type() == ResponseFormatType.JSON) {
            configBuilder.responseMimeType("application/json");
            if (responseFormat.jsonSchema() != null
                    && responseFormat.jsonSchema().rootElement() != null) {
                JsonSchemaElement rootElement = responseFormat.jsonSchema().rootElement();
                if (GoogleGenAiToolMapper.canBeMapped(rootElement)) {
                    Schema googleSchema = GoogleGenAiToolMapper.convertToGoogleSchema(rootElement);
                    configBuilder.responseSchema(googleSchema);
                } else {
                    configBuilder.responseJsonSchema(toMap(rootElement));
                }
            }
        }

        if (thinkingBudget != null && thinkingLevel != null) {
            throw new IllegalArgumentException("Cannot use both thinkingBudget and thinkingLevel at the same time");
        }

        if (thinkingBudget != null || thinkingLevel != null || includeThoughts != null) {
            ThinkingConfig.Builder thinkingBuilder = ThinkingConfig.builder();
            if (thinkingBudget != null) {
                thinkingBuilder.thinkingBudget(thinkingBudget);
            }
            if (thinkingLevel != null) {
                thinkingBuilder.thinkingLevel(new ThinkingLevel(thinkingLevel));
            }
            if (includeThoughts != null) {
                thinkingBuilder.includeThoughts(includeThoughts);
            }
            configBuilder.thinkingConfig(thinkingBuilder.build());
        }

        if (seed != null) {
            configBuilder.seed(seed);
        }

        if (systemInstruction != null) {
            configBuilder.systemInstruction(systemInstruction);
        }

        Map<String, String> effectiveLabels = labels;
        String effectiveCachedContent = cachedContent;
        Boolean effectiveAutomaticContinuation = automaticContinuation;
        if (parameters instanceof GoogleGenAiChatRequestParameters googleGenAiParameters) {
            effectiveLabels = getOrDefault(googleGenAiParameters.labels(), effectiveLabels);
            effectiveCachedContent = getOrDefault(googleGenAiParameters.cachedContent(), effectiveCachedContent);
            effectiveAutomaticContinuation =
                    getOrDefault(googleGenAiParameters.automaticContinuation(), effectiveAutomaticContinuation);
        }

        if (effectiveLabels != null) {
            configBuilder.labels(effectiveLabels);
        }

        if (effectiveCachedContent != null && !effectiveCachedContent.trim().isEmpty()) {
            configBuilder.cachedContent(effectiveCachedContent);
        }

        if (audioTranscriptionConfig != null) {
            configBuilder.audioTranscriptionConfig(audioTranscriptionConfig);
        }

        if (speechConfig != null) {
            configBuilder.speechConfig(speechConfig);
        }

        if (!isNullOrEmpty(responseModalities)) {
            configBuilder.responseModalities(responseModalities);
        }

        if (effectiveAutomaticContinuation != null) {
            configBuilder.automaticContinuation(effectiveAutomaticContinuation);
        }

        buildTools(
                configBuilder,
                parameters,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                codeExecutionEnabled,
                allowedFunctionNames,
                vertexSearchDatastore);

        if (generateContentConfigCustomizer != null) {
            generateContentConfigCustomizer.accept(configBuilder);
        }
        return configBuilder.build();
    }

    private static void buildTools(
            GenerateContentConfig.Builder configBuilder,
            ChatRequestParameters parameters,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            boolean codeExecutionEnabled,
            List<String> allowedFunctionNames,
            String vertexSearchDatastore) {

        List<ToolSpecification> toolSpecs = parameters.toolSpecifications();

        List<Tool> requestTools = new ArrayList<>();
        List<FunctionDeclaration> functionDeclarations = new ArrayList<>();

        if (toolSpecs != null) {
            for (ToolSpecification toolSpecification : toolSpecs) {
                functionDeclarations.add(GoogleGenAiToolMapper.convertToGoogleFunction(toolSpecification));
            }
        }

        if (!functionDeclarations.isEmpty()) {
            Tool functionTool =
                    Tool.builder().functionDeclarations(functionDeclarations).build();
            requestTools.add(functionTool);
        }

        if (googleSearchEnabled) {
            requestTools.add(
                    Tool.builder().googleSearch(GoogleSearch.builder().build()).build());
        }
        if (googleMapsEnabled) {
            requestTools.add(
                    Tool.builder().googleMaps(GoogleMaps.builder().build()).build());
        }
        if (urlContextEnabled) {
            requestTools.add(
                    Tool.builder().urlContext(UrlContext.builder().build()).build());
        }
        if (codeExecutionEnabled) {
            requestTools.add(Tool.builder()
                    .codeExecution(ToolCodeExecution.builder().build())
                    .build());
        }
        if (vertexSearchDatastore != null && !vertexSearchDatastore.isEmpty()) {
            requestTools.add(Tool.builder()
                    .retrieval(Retrieval.builder()
                            .vertexAiSearch(VertexAISearch.builder()
                                    .datastore(vertexSearchDatastore)
                                    .build())
                            .build())
                    .build());
        }

        if (!requestTools.isEmpty()) {
            configBuilder.tools(requestTools);
        }

        if (!isNullOrEmpty(toolSpecs)) {
            FunctionCallingConfig.Builder funcConfig = FunctionCallingConfig.builder();

            ToolChoice toolChoice = parameters.toolChoice();
            if (toolChoice == ToolChoice.REQUIRED) {
                funcConfig.mode("ANY");
            } else if (toolChoice == ToolChoice.NONE) {
                funcConfig.mode("NONE");
            } else {
                funcConfig.mode("AUTO");
            }

            if (!isNullOrEmpty(allowedFunctionNames)) {
                funcConfig.allowedFunctionNames(allowedFunctionNames);
            }

            configBuilder.toolConfig(ToolConfig.builder()
                    .functionCallingConfig(funcConfig.build())
                    .build());
        }
    }

    private GoogleGenAiConfigBuilder() {}
}
