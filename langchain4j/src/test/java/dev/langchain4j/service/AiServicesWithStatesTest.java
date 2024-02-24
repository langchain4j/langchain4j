package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import static dev.langchain4j.service.AiServicesWithStatesTest.State.*;
import static java.util.Collections.singleton;

class AiServicesWithStatesTest {

    interface Agent {

        String chat(String userMessage);
    }

    enum State {
        START, BOOKING_IDENTIFIED, CONFIRMING_CANCELLATION
    }

    static class BookingIdentificationService {

        @Tool
        boolean bookingExists(String bookingNumber, String userName, String userSurname) {
            System.out.println("=========================");
            System.out.println(" VERIFYING " + bookingNumber + " " + userName + " " + userSurname);
            System.out.println("=========================");
            return bookingNumber.equals("123-456") && userName.equals("Klaus") && userSurname.equals("Heisler");
        }
    }

    static class BookingDetailsService {

        @Tool
        String getBookingDetails(String bookingNumber, String userName, String userSurname) {
            System.out.println("=========================");
            System.out.println(" PROVIDING DETAILS " + bookingNumber + " " + userName + " " + userSurname);
            System.out.println("=========================");
            return "Booking is from 12 Mar to 17 Mar 2024"; // TODO
        }
    }

    static class BookingCancellationService {

        @Tool
        void cancelBooking(String bookingNumber, String userName, String userSurname) {
            System.out.println("=========================");
            System.out.println(" CANCELLING " + bookingNumber + " " + userName + " " + userSurname);
            System.out.println("=========================");
        }
    }

    public static void main(String[] args) {

        ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey("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") // TODO !!!
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .temperature(0.0)
//            .logRequests(true)
//            .logResponses(true)
                .build();

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

        String common = "You are a customer support agent of a car rental company named 'Miles of Smiles'. ";

        Map<Enum<?>, String> stateToSystemMessage = new HashMap<>();
        stateToSystemMessage.put(START,
                common +
                        "The conversation is in a START state. " + // TODO generate current state info automatically
                        "If user asks to get booking details or cancel booking, you need to ask for a booking number and users name and surname. " +
                        "Once user provided all details, move to BOOKING_IDENTIFIED state. " +
                        "Today is {{current_date}}.");
        stateToSystemMessage.put(BOOKING_IDENTIFIED,
                common +
                        "The conversation is in a BOOKING_IDENTIFIED state. " +
                        "If user wants to cancel the booking, move to CONFIRMING_CANCELLATION state." +
                        "Today is {{current_date}}.");
        stateToSystemMessage.put(CONFIRMING_CANCELLATION,
                common +
                        "The conversation is in a CONFIRMING_CANCELLATION state. " +
                        "Ask user if he is sure to cancel the booking? If yes, cancel it. " +
                        "Today is {{current_date}}.");

        Map<Enum<?>, Set<Enum<?>>> allowedTransitions = new HashMap<>();
        allowedTransitions.put(START, singleton(BOOKING_IDENTIFIED)); // TODO give an option to define a function/lambda to validate state during transition from one state to another. bookingExists(...) should be forcefully called by the state machine, not by the LLM
        allowedTransitions.put(BOOKING_IDENTIFIED, singleton(CONFIRMING_CANCELLATION));
        allowedTransitions.put(CONFIRMING_CANCELLATION, singleton(BOOKING_IDENTIFIED));

        Agent agent = AiServices.builder(Agent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .states(State.class, START, allowedTransitions)
                .systemMessages(stateToSystemMessage) // TODO give an option to define system messages per sate, as with tools below
                .tools(START, new BookingIdentificationService())
                .tools(BOOKING_IDENTIFIED, new BookingDetailsService())
                .tools(CONFIRMING_CANCELLATION, new BookingCancellationService())
                // TODO allow defining a "state" DTO (LLM's scratchpad) that will be passed around, LLM can store info there, for example booking number, name and surname. So that these things are stored not in the chat memory that can be dropped (evicted) in a long discussion, but in a state DTO that can be persisted and all details from it always accessible to the LLM (e.g. can be putin a system prompt)
                .build();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("User: ");
            String userMessage = scanner.nextLine();

            if ("exit".equalsIgnoreCase(userMessage)) {
                break;
            }

            String agentMessage = agent.chat(userMessage);
            System.out.println("Agent: " + agentMessage);
        }

        scanner.close();

//        System.out.println(agent.chat("hi"));
//        System.out.println(agent.chat("cancel my booking"));
//        System.out.println(agent.chat("123-456"));
//        System.out.println(agent.chat("Klaus Heisler"));
//        System.out.println(agent.chat("yeah"));

    }

}
