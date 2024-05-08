package dev.langchain4j.service;

import java.time.Duration;
import java.util.Scanner;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.UserMessagesConcatChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class AiServicesExtractorMemoryTest {

    public static void main(String[] args) {

        ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("mistral")
                .temperature(0.1)
                .timeout(Duration.ofMinutes(3))
                .build();

        ChatMemory chatMemory = UserMessagesConcatChatMemory.build();

        CustomerExtractor customerExtractor = AiServices.builder(CustomerExtractor.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        Scanner scanner = new Scanner(System.in);
        Customer customer = null;

        while (customer == null || !customer.isValid()) {
            System.out.print("User: ");
            String userMessage = scanner.nextLine();

            try {
                customer = customerExtractor.extractData(userMessage);
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }

        System.out.println("Extracted " + customer);

        scanner.close();
    }

    public static class Customer {
        public final String firstName;
        public final String lastName;
        public final int age;

        public Customer(String firstName, String lastName, int age) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }

        @Override
        public String toString() {
            return "Customer {" +
                    " firstName = \"" + firstName + "\"" +
                    ", lastName = \"" + lastName + "\"" +
                    ", age = " + age +
                    " }";
        }

        public String getFullName() {
            return firstName + " " + lastName;
        }

        public boolean isValid() {
            return firstName != null && !firstName.isEmpty() && age > 0;
        }
    }

    interface CustomerExtractor {

        @UserMessage("Extract information about a customer from this text '{{it}}'. The response must contain only the JSON with customer's data and without any other sentence.")
        Customer extractData(String text);
    }
}
