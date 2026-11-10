package com.example.support;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class BookingTools {

    private final Set<String> cancelledBookings = ConcurrentHashMap.newKeySet();

    @Tool("Returns the details of a booking")
    public String getBookingDetails(String bookingNumber) {
        if (!bookingNumber.startsWith("MS-")) {
            throw new BookingNotFoundException(bookingNumber);
        }
        return "Booking " + bookingNumber + ": compact car, 3 days";
    }

    @Tool("Cancels a booking")
    public String cancelBooking(String bookingNumber) {
        cancelledBookings.add(bookingNumber);
        return "Booking " + bookingNumber + " cancelled";
    }

    public boolean isCancelled(String bookingNumber) {
        return cancelledBookings.contains(bookingNumber);
    }
}
