package org.example;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws SQLException {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(getOpenAiApiKey())
                .baseUrl("https://openrouter.ai/api/v1")
                // Pick a model available in your OpenRouter account:
                .modelName("openai/gpt-4o-mini")
                .maxTokens(512)
                // Recommended by OpenRouter for attribution/analytics:
                .customHeaders(Map.of(
                        "HTTP-Referer", "https://your-app.example",  // can be your internal site/repo URL
                        "X-Title", "langchain4j-demo"
                ))
                .build();
        Chatmemorystore memorystore=new Chatmemorystore(Duration.ofHours(1));
        String memoryId = "user123-sessionA";
        ChatMemory chatMemory=MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(memorystore)
                .build();
        Assistant assistant= AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .build();
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                String user = sc.nextLine();
                if ("exit".equalsIgnoreCase(user)) break;

                String answer = assistant.chat(user);
                System.out.println("Bot: " + answer);
            }
        }

    }
    static String getOpenAiApiKey() {
        Properties p = new Properties();
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (in == null) throw new IllegalStateException("app.properties not found");
            p.load(in);

            // app.properties contains the NAME of the env var, e.g. OPENROUTER_API_KEY
            String envVarName = p.getProperty("API_KEY_ENV_NAME");
            if (envVarName == null || envVarName.isBlank()) {
                throw new IllegalStateException("API_KEY_ENV_NAME missing in app.properties");
            }

            return envVarName;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }}