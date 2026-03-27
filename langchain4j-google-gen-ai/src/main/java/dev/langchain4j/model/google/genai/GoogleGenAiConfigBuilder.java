package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GoogleMaps;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.UrlContext;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.ArrayList;
import java.util.List;

class GoogleGenAiConfigBuilder {

    static GenerateContentConfig buildConfig(
            ChatRequestParameters parameters,
            Content systemInstruction,
            List<SafetySetting> safetySettings,
            Schema responseSchema,
            String responseMimeType,
            Integer thinkingBudget,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            List<String> allowedFunctionNames) {

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

        // Generation parameters
        if (parameters.temperature() != null)
            configBuilder.temperature(parameters.temperature().floatValue());
        if (parameters.topP() != null) configBuilder.topP(parameters.topP().floatValue());
        if (parameters.topK() != null) configBuilder.topK(parameters.topK().floatValue());
        if (parameters.maxOutputTokens() != null) configBuilder.maxOutputTokens(parameters.maxOutputTokens());
        if (parameters.stopSequences() != null) configBuilder.stopSequences(parameters.stopSequences());

        // Safety settings
        if (safetySettings != null && !safetySettings.isEmpty()) {
            configBuilder.safetySettings(safetySettings);
        }

        // Response format
        if (responseMimeType != null) {
            configBuilder.responseMimeType(responseMimeType);
        }

        if (responseSchema != null) {
            configBuilder.responseSchema(responseSchema);
            configBuilder.responseMimeType("application/json");
        }

        if (parameters.responseFormat() != null) {
            if (parameters.responseFormat().type() == ResponseFormatType.JSON) {
                configBuilder.responseMimeType("application/json");
            }
        }

        // Thinking config
        if (thinkingBudget != null) {
            configBuilder.thinkingConfig(
                    ThinkingConfig.builder().thinkingBudget(thinkingBudget).build());
        }

        // Seed
        if (seed != null) {
            configBuilder.seed(seed);
        }

        // System instruction
        if (systemInstruction != null) {
            configBuilder.systemInstruction(systemInstruction);
        }

        // Tools
        buildTools(configBuilder, parameters, googleSearchEnabled, googleMapsEnabled, urlContextEnabled, allowedFunctionNames);

        return configBuilder.build();
    }

    private static void buildTools(
            GenerateContentConfig.Builder configBuilder,
            ChatRequestParameters parameters,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            List<String> allowedFunctionNames) {

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
            requestTools.add(Tool.builder().googleSearch(GoogleSearch.builder().build()).build());
        }
        if (googleMapsEnabled) {
            requestTools.add(Tool.builder().googleMaps(GoogleMaps.builder().build()).build());
        }
        if (urlContextEnabled) {
            requestTools.add(Tool.builder().urlContext(UrlContext.builder().build()).build());
        }

        if (!requestTools.isEmpty()) {
            configBuilder.tools(requestTools);
        }

        if (!isNullOrEmpty(toolSpecs)) {
            FunctionCallingConfig.Builder funcConfig = FunctionCallingConfig.builder();

            if (parameters.toolChoice() == ToolChoice.REQUIRED) {
                funcConfig.mode("ANY");
            } else if (parameters.toolChoice() == ToolChoice.NONE) {
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
