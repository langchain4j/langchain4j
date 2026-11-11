package com.example.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BookingRepository {

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public BookingRepository add(Booking booking) {
        bookings.put(booking.bookingNumber(), booking);
        return this;
    }

    public Booking find(String bookingNumber) {
        Booking booking = bookings.get(bookingNumber);
        if (booking == null) {
            throw new BookingNotFoundException(bookingNumber);
        }
        return booking;
    }

    public Booking cancel(String bookingNumber) {
        return bookings.compute(bookingNumber, (number, booking) -> {
            if (booking == null) {
                throw new BookingNotFoundException(number);
            }
            return booking.cancel();
        });
    }
}
