package dev.langchain4j.agentic.carrentalassistant.domain;

import dev.langchain4j.model.output.structured.Description;

/**
 * Represents customer information during a session.
 */
public class CustomerInfo {
    private String name;
    private String customerId;
    private String bookingReference;
    private String carMake;
    private String carModel;
    private String carYear;
    @Description("Where the customer is located (e.g. airport, city, an address, etc.)")
    private String location;

    /**
     * Default constructor for JSON serialization/deserialization.
     */
    public CustomerInfo() {
    }

    /**
     * Creates a new CustomerInfo with the specified details.
     *
     * @param name            The customer's name
     * @param customerId      The customer's ID
     * @param bookingReference The booking reference number
     * @param carMake         The make of the rental car
     * @param carModel        The model of the rental car
     * @param carYear         The year of the rental car
     * @param location        The customer's current location
     */
    public CustomerInfo(String name, String customerId, String bookingReference,
                        String carMake, String carModel, String carYear,
                        String location) {
        this.name = name;
        this.customerId = customerId;
        this.bookingReference = bookingReference;
        this.carMake = carMake;
        this.carModel = carModel;
        this.carYear = carYear;
        this.location = location;
    }

    /**
     * Checks if the customer information is complete.
     *
     * @return true if all essential information is available, false otherwise
     */
    public boolean isComplete() {
        return name != null && !name.isEmpty() &&
               bookingReference != null && !bookingReference.isEmpty() &&
               carMake != null && !carMake.isEmpty() &&
               carModel != null && !carModel.isEmpty();
    }

    /**
     * Returns a string representation of the customer information.
     *
     * @return A formatted string with customer details
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (name != null && !name.isEmpty()) {
            sb.append("Name: ").append(name).append("\n");
        }
        
        if (customerId != null && !customerId.isEmpty()) {
            sb.append("Customer ID: ").append(customerId).append("\n");
        }
        
        if (bookingReference != null && !bookingReference.isEmpty()) {
            sb.append("Booking Reference: ").append(bookingReference).append("\n");
        }
        
        if (carMake != null && !carMake.isEmpty() || carModel != null && !carModel.isEmpty() || carYear != null && !carYear.isEmpty()) {
            sb.append("Vehicle: ");
            if (carYear != null && !carYear.isEmpty()) {
                sb.append(carYear).append(" ");
            }
            if (carMake != null && !carMake.isEmpty()) {
                sb.append(carMake).append(" ");
            }
            if (carModel != null && !carModel.isEmpty()) {
                sb.append(carModel);
            }
            sb.append("\n");
        }
        
        if (location != null && !location.isEmpty()) {
            sb.append("Location: ").append(location).append("\n");
        }
        
        return sb.toString().trim();
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getCarMake() {
        return carMake;
    }

    public void setCarMake(String carMake) {
        this.carMake = carMake;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getCarYear() {
        return carYear;
    }

    public void setCarYear(String carYear) {
        this.carYear = carYear;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
