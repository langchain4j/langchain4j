package com.example.support;

public record Booking(String bookingNumber, String customerName, String startDate, String endDate, boolean cancelled) {

    Booking cancel() {
        return new Booking(bookingNumber, customerName, startDate, endDate, true);
    }
}
