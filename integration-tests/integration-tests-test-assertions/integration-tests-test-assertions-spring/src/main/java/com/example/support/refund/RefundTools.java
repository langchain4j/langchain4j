package com.example.support.refund;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class RefundTools {

    @Audited
    @Tool("Issues a refund in euros for the booking with the given number")
    public String issueRefund(String bookingNumber, int amountInEuros) {
        return "Refund of " + amountInEuros + " EUR issued for booking " + bookingNumber;
    }
}
