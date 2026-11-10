package com.example.support.assistant;

import com.example.support.booking.BookingSummary;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Mono;

public interface SupportAssistant {

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    String chat(String message);

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    Result<String> chatWithDetails(String message);

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    BookingSummary summarizeBooking(String message);

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    Mono<String> chatLater(String message);

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    Mono<BookingSummary> summarizeBookingLater(String message);
}
