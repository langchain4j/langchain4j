package com.example.support;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import java.util.concurrent.CompletableFuture;

public interface BookingRequestExtractor {

    @UserMessage("Extract the booking request from this email: {{it}}")
    BookingRequest extract(String email);

    @UserMessage("Extract the booking request from this email: {{it}}")
    CompletableFuture<BookingRequest> extractAsync(String email);

    @UserMessage("Extract the booking request from this email: {{it}}")
    Result<BookingRequest> extractWithDetails(String email);
}
