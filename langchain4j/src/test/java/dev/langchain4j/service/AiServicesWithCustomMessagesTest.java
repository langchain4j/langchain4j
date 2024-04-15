package dev.langchain4j.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

import static dev.langchain4j.service.AiServicesWithCustomMessagesTest.AirlineChatState.CALCULATE_REFUND;
import static dev.langchain4j.service.AiServicesWithCustomMessagesTest.AirlineChatState.EXTRACT_CUSTOMER;
import static dev.langchain4j.service.AiServicesWithCustomMessagesTest.AirlineChatState.EXTRACT_FLIGHT;

class AiServicesWithCustomMessagesTest {

    interface Agent {
        String chat(@MemoryId Object memoryId, @UserMessage String userMessage);
    }

    enum AirlineChatState {
        EXTRACT_CUSTOMER("<SYS>>You are a chat bot of an airline company. Your goal is asking questions to gather information " +
                                 "about a customer<</SYS>>",
                         "Ask question to the customer regarding his name and age. +++ {{it}} +++ "),

        EXTRACT_FLIGHT("<<SYS>>You are a chat bot of an airline company. Your goal is asking questions to gather information " +
                               "about the customer's flight and which problems he had with it<</SYS>>",
                       "Ask question to the customer regarding the number of the flight and its eventual delay. +++ {{it}} +++ "),
        CALCULATE_REFUND("", "");

        private final String systemMessage;
        private final String userMessage;

        AirlineChatState(String systemMessage, String userMessage) {
            this.systemMessage = systemMessage;
            this.userMessage = userMessage;
        }

        public String getSystemMessage() {
            return systemMessage;
        }

        public String getUserMessage() {
            return userMessage;
        }
    }

    public static void main(String[] args) {

        ChatLanguageModel chatLanguageModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("mistral")
                .temperature(0.1)
                .timeout(Duration.ofMinutes(3))
                .build();

        AirlineChatContext context = new AirlineChatContext();
        int sessionId = 1;

        Map<Object, AirlineChatMessagesProvider> messagesProviderByUser = new HashMap<>();
        messagesProviderByUser.put(sessionId, new AirlineChatMessagesProvider(context));

        Map<Object, ChatMemory> chatMemories = new HashMap<>();
        chatMemories.put(sessionId, MessageWindowChatMemory.withMaxMessages(20));

        Agent agent = AiServices.builder(Agent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemories::get)
                .userMessageProvider(memoryId -> messagesProviderByUser.get(memoryId).userMessage())
                .systemMessageProvider(memoryId -> messagesProviderByUser.get(memoryId).systemMessage())
                .build();

        CustomerExtractor customerExtractor = AiServices.builder(CustomerExtractor.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        FlightExtractor flightExtractor = AiServices.builder(FlightExtractor.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        Scanner scanner = new Scanner(System.in);

        while (!context.isComplete()) {
            System.out.print("User: ");
            String userMessage = scanner.nextLine();

            if ("exit".equalsIgnoreCase(userMessage)) {
                break;
            }

            if (context.getCustomer() == null) {
                try {
                    Customer customer = customerExtractor.extractData(userMessage);
                    if (customer != null && customer.isValid()) {
                        System.out.println("Extracted " + customer);
                        context.setCustomer(customer);
                    }
                } catch (Exception ignore) { }
            } else if (context.getFlight() == null) {
                try {
                    Flight flight = flightExtractor.extractData(userMessage);
                    if (flight != null && flight.isValid()) {
                        System.out.println("Extracted " + flight);
                        context.setFlight(flight);
                    }
                } catch (Exception ignore) { }
            }

            String agentMessage = context.isComplete() ?
                    "Thank you " + context.getCustomer().getFullName() + ", you are eligible for a refund of $" + context.getRefund() :
                    agent.chat(sessionId, userMessage);
            System.out.println("Agent: " + agentMessage);
        }

        scanner.close();
    }

    public interface Validated {
        boolean isValid();
    }

    public static class Customer implements Validated {
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

        @Override
        public boolean isValid() {
            return firstName != null && !firstName.isEmpty() && age > 0;
        }
    }

    public static class Flight implements Validated {

        public final String number;
        public final int delayInMinutes;

        public Flight(String number, int delayInMinutes) {
            this.number = number;
            this.delayInMinutes = delayInMinutes;
        }

        @Override
        public String toString() {
            return "Flight {" +
                    " number = \"" + number + "\"" +
                    ", delayInMinutes = " + delayInMinutes +
                    " }";
        }

        @Override
        public boolean isValid() {
            return number != null && !number.isEmpty() && delayInMinutes > 0;
        }
    }

    interface CustomerExtractor {

        @UserMessage("Extract information about a customer from this text '{{it}}'. The response must contain only the JSON with customer's data and without any other sentence.")
        Customer extractData(String text);
    }

    interface FlightExtractor {
        @UserMessage("Extract information about a flight from this text '{{it}}'. The response must contain only the JSON with flight's data and without any other sentence.")
        Flight extractData(String text);
    }

    public static class AirlineChatContext {

        private AirlineChatState currentState = EXTRACT_CUSTOMER;

        private Customer customer;

        private Flight flight;

        public Customer getCustomer() {
            return customer;
        }

        public void setCustomer(Customer customer) {
            this.customer = customer;
        }

        public Flight getFlight() {
            return flight;
        }

        public void setFlight(Flight flight) {
            this.flight = flight;
        }

        public boolean isComplete() {
            return customer != null && flight != null;
        }

        public double getRefund() {
            if (flight.delayInMinutes < 60) {
                return 0;
            }
            double refund = flight.delayInMinutes * 2;
            if (customer.age > 65) {
                refund = refund * 1.1;
            }
            return refund;
        }

        @Override
        public String toString() {
            return "SessionData{" +
                    "customer=" + customer +
                    ", flight=" + flight +
                    '}';
        }

        private AirlineChatState getState() {
            if (currentState == EXTRACT_CUSTOMER && getCustomer() != null) {
                currentState = EXTRACT_FLIGHT;
            } else if (currentState == EXTRACT_FLIGHT && getFlight() != null) {
                currentState = CALCULATE_REFUND;
            }
            return currentState;
        }
    }

    public static class AirlineChatMessagesProvider {
        private final AirlineChatContext context;

        public AirlineChatMessagesProvider(AirlineChatContext context) {
            this.context = context;
        }

        public String systemMessage() {
            return context.getState().getSystemMessage();
        }

        public String userMessage() {
            return context.getState().getUserMessage();
        }
    }
}
