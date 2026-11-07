package com.example.support;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class BookingTools {

    private final BookingRepository bookingRepository;

    public BookingTools(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Tool("Returns the details of a booking")
    public Booking getBookingDetails(
            @P(name = "bookingNumber", description = "The booking number, for example MS-777") String bookingNumber) {
        return bookingRepository.find(bookingNumber);
    }

    @Tool("Cancels a booking")
    public String cancelBooking(
            @P(name = "bookingNumber", description = "The booking number, for example MS-777") String bookingNumber) {
        bookingRepository.cancel(bookingNumber);
        return "Booking " + bookingNumber + " cancelled";
    }

    @Tool("Calculates the refund for a cancelled booking")
    public double calculateRefund(
            @P(name = "bookingNumber", description = "The booking number, for example MS-777") String bookingNumber,
            @P(name = "days", description = "The number of rental days to refund") int days) {
        bookingRepository.find(bookingNumber);
        return days * 49.5;
    }
}
