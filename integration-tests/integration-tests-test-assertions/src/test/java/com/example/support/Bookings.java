package com.example.support;

final class Bookings {

    private Bookings() {}

    static BookingRepository bookings() {
        return new BookingRepository()
                .add(new Booking("MS-777", "Klaus Heisler", "2026-11-02", "2026-11-05", false))
                .add(new Booking("MS-888", "Sylvie Paquet", "2026-12-20", "2026-12-27", false));
    }
}
