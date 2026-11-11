package com.example.support.booking;

import dev.langchain4j.agent.tool.Tool;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class BookingTools {

    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    @Tool("Returns the details of the booking with the given number")
    public String getBookingDetails(String bookingNumber) {
        ensureExists(bookingNumber);
        return "Booking " + bookingNumber + ": compact car, 2026-11-01 to 2026-11-05";
    }

    @Tool("Cancels the booking with the given number")
    public String cancelBooking(String bookingNumber) {
        ensureExists(bookingNumber);
        cancelled.add(bookingNumber);
        return "Booking " + bookingNumber + " cancelled";
    }

    public boolean isCancelled(String bookingNumber) {
        return cancelled.contains(bookingNumber);
    }

    private static void ensureExists(String bookingNumber) {
        if (!bookingNumber.startsWith("MS-")) {
            throw new BookingNotFoundException(bookingNumber);
        }
    }
}
