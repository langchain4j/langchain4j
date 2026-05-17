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
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import com.google.genai.types.UrlContext;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import java.util.ArrayList;
import java.util.List;

class GoogleGenAiConfigBuilder {

    static GenerateContentConfig buildConfig(
            ChatRequestParameters parameters,
            Content systemInstruction,
            List<SafetySetting> safetySettings,
            Integer thinkingBudget,
            Integer seed,
            boolean googleSearchEnabled,
            boolean googleMapsEnabled,
            boolean urlContextEnabled,
            List<String> allowedFunctionNames) {

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
            if (responseFormat.jsonSchema() != null) {
                Schema googleSchema = GoogleGenAiToolMapper.convertToGoogleSchema(
                        responseFormat.jsonSchema().rootElement());
                configBuilder.responseSchema(googleSchema);
            }
        }

        if (thinkingBudget != null) {
            configBuilder.thinkingConfig(
                    ThinkingConfig.builder().thinkingBudget(thinkingBudget).build());
        }

        if (seed != null) {
            configBuilder.seed(seed);
        }

        if (systemInstruction != null) {
            configBuilder.systemInstruction(systemInstruction);
        }

        buildTools(
                configBuilder,
                parameters,
                googleSearchEnabled,
                googleMapsEnabled,
                urlContextEnabled,
                allowedFunctionNames);

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
