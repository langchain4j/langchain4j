package com.example;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
//import dev.langchain4j.model.gpullama3;
import dev.langchain4j.model.gpullama3.GpuLlama3ChatModel;
import java.nio.file.Path;
import java.nio.file.Paths;


public class App {
    public static void main(String[] args) {
        Path modelPath = Paths.get("/Users/iain/Work/beehive/GPULlama3.java/beehive-llama-3.2-1b-instruct-fp16.gguf");
        ChatModel model = GpuLlama3ChatModel
                .builder()
                .modelPath(modelPath)
                .build();

        String res = model.chat("Hello from Tornado?");
        System.out.println(res);
    }
}

