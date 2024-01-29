package dev.langchain4j.classification;

import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.classification.EmbeddingModelTextClassifierTest.CustomerServiceCategory.*;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingModelTextClassifierTest {

    enum CustomerServiceCategory {

        BILLING_AND_PAYMENTS,
        TECHNICAL_SUPPORT,
        ACCOUNT_MANAGEMENT,
        PRODUCT_INFORMATION,
        ORDER_STATUS,
        RETURNS_AND_EXCHANGES,
        FEEDBACK_AND_COMPLAINTS
    }

    private final Map<CustomerServiceCategory, List<String>> examples = new HashMap<>();

    {
        examples.put(BILLING_AND_PAYMENTS, asList(
                "Can I pay using PayPal?",
                "Do you accept Bitcoin?",
                "Is it possible to pay via wire transfer?",
                "I keep getting an error message when I try to pay.",
                "My card was charged twice, can you help?",
                "Why was my payment declined?",
                "How can I request a refund?",
                "When will I get my refund?",
                "Can I get a refund if I cancel my subscription?",
                "Can you send me an invoice for my last order?",
                "I didn't receive a receipt for my purchase.",
                "Is the invoice sent to my email automatically?",
                "How do I upgrade my subscription?",
                "What are the differences between the Basic and Premium plans?",
                "How do I cancel my subscription?",
                "Can I switch to a monthly plan from an annual one?",
                "I want to downgrade my subscription, how do I go about it?",
                "Is there a penalty for downgrading my plan?"
        ));
        examples.put(TECHNICAL_SUPPORT, asList(
                "The app keeps crashing whenever I open it.",
                "I can't save changes in the settings.",
                "Why is the search function not working?",
                "The installer is stuck at 50%.",
                "I keep getting an ‘Installation Failed' message.",
                "How do I install this on a Mac?",
                "I can't connect to the server.",
                "Why am I constantly getting disconnected?",
                "My Wi-Fi works, but your app says no internet connection.",
                "Why is the app so slow?",
                "I'm experiencing lag during video calls.",
                "The website keeps freezing on my browser.",
                "I get a ‘404 Not Found' error.",
                "What does the ‘Permission Denied' error mean?",
                "Why am I seeing an ‘Insufficient Storage' warning?",
                "Is this compatible with Windows 11?",
                "The app doesn't work on my Android phone.",
                "Do you have a browser extension for Safari?"
        ));
        examples.put(ACCOUNT_MANAGEMENT, asList(
                "I forgot my password, how can I reset it?",
                "I didn't receive a password reset email.",
                "Is there a way to change my password from within the app?",
                "How do I set up two-factor authentication?",
                "I lost my phone, how can I log in now?",
                "Why am I not getting the 2FA code?",
                "My account has been locked, what do I do?",
                "Is there a limit on login attempts?",
                "I've been locked out for no reason, can you help?",
                "How do I change my email address?",
                "Can I update my profile picture?",
                "How do I edit my shipping address?",
                "Can I share my account with family?",
                "How do I give admin access to my team member?",
                "Is there a guest access feature?",
                "How do I delete my account?",
                "What happens to my data if I deactivate my account?",
                "Can I reactivate my account later?"
        ));
        examples.put(PRODUCT_INFORMATION, asList(
                "What does the ‘Sync' feature do?",
                "How does the privacy mode work?",
                "Can you explain the real-time tracking feature?",
                "When will the new model be in stock?",
                "Do you have this item in a size medium?",
                "Are you restocking the sold-out items soon?",
                "What's the difference between version 1.0 and 2.0?",
                "Is the Pro version worth the extra cost?",
                "Do older versions support the new update?",
                "Is this product compatible with iOS?",
                "Will this work with a 220V power supply?",
                "Do you have options for USB-C?",
                "Are there any accessories included?",
                "Do you sell protective cases for this model?",
                "What add-ons would you recommend?",
                "What does the warranty cover?",
                "How do I claim the warranty?",
                "Is the warranty international?"
        ));
        examples.put(ORDER_STATUS, asList(
                "Where is my order right now?",
                "Can you give me a tracking number?",
                "How do I know my order has been shipped?",
                "Can I change the shipping method?",
                "Do you offer overnight shipping?",
                "Is pickup from the store an option?",
                "When will my order arrive?",
                "Why is my delivery delayed?",
                "Can I specify a delivery date?",
                "It's past the delivery date, where is my order?",
                "Will I be notified if there's a delay?",
                "How long will the weather delay my shipment?",
                "I received my order, but an item is missing.",
                "The package was empty when it arrived.",
                "I got the wrong item, what should I do?",
                "Will all my items arrive at the same time?",
                "Why did I receive only part of my order?",
                "Is it possible to get the remaining items faster?"
        ));
        examples.put(RETURNS_AND_EXCHANGES, asList(
                "What's your return policy?",
                "Is the return shipping free?",
                "Do I need the original packaging to return?",
                "How do I get a return label?",
                "Do I need to call customer service for a return?",
                "Is an RMA number required?",
                "I need to exchange for a different size.",
                "Can I exchange a gift?",
                "How long does the exchange process take?",
                "My item arrived damaged, what do I do?",
                "The product doesn't work as described.",
                "There's a part missing, can you send it?",
                "I received the wrong item, how can I get it corrected?",
                "I didn't order this, why did I receive it?",
                "You sent me two of the same item by mistake.",
                "Is there a restocking fee for returns?",
                "Will I get a full refund?",
                "How much will be deducted for restocking?"
        ));
        examples.put(FEEDBACK_AND_COMPLAINTS, asList(
                "The material quality is not as advertised.",
                "The product broke after a week of use.",
                "The colors faded after the first wash.",
                "The representative was rude to me.",
                "I was on hold for 30 minutes, this is unacceptable.",
                "Your customer service resolved my issue quickly, thank you!",
                "Your website is hard to navigate.",
                "The app keeps crashing, it's frustrating.",
                "The checkout process is confusing.",
                "You should offer a chat feature for quicker help.",
                "Can you add a wishlist feature?",
                "Please make a mobile-friendly version of the website.",
                "I found a bug in your software.",
                "There's a typo on your homepage.",
                "The payment page has a glitch.",
                "Can you start offering this in a gluten-free option?",
                "Please add support for Linux.",
                "I wish you had more colors to choose from."
        ));
    }

    @Test
    void should_return_one_category_by_default() {

        TextClassifier<CustomerServiceCategory> classifier = new EmbeddingModelTextClassifier<>(
                new AllMiniLmL6V2QuantizedEmbeddingModel(),
                examples
        );

        List<CustomerServiceCategory> categories = classifier.classify("Yo where is my order?");

        assertThat(categories).containsExactly(ORDER_STATUS);
    }

    @Test
    void should_return_multiple_categories() {

        TextClassifier<CustomerServiceCategory> classifier = new EmbeddingModelTextClassifier<>(
                new AllMiniLmL6V2QuantizedEmbeddingModel(),
                examples,
                2,
                0,
                0.5
        );

        List<CustomerServiceCategory> categories = classifier.classify("Bro, this product is crap");

        assertThat(categories).containsExactly(RETURNS_AND_EXCHANGES, FEEDBACK_AND_COMPLAINTS);
    }

    @Test
    void should_classify_respecting_minScore() {

        TextClassifier<CustomerServiceCategory> classifier = new EmbeddingModelTextClassifier<>(
                new AllMiniLmL6V2QuantizedEmbeddingModel(),
                examples,
                2,
                0.64,
                0.5
        );

        List<CustomerServiceCategory> categories = classifier.classify("Bro, this product is crap");

        assertThat(categories).containsExactly(RETURNS_AND_EXCHANGES);
    }

    @Test
    void should_classify_respecting_meanToMaxScoreRatio_1() {

        TextClassifier<CustomerServiceCategory> classifier = new EmbeddingModelTextClassifier<>(
                new AllMiniLmL6V2QuantizedEmbeddingModel(),
                examples,
                1,
                0,
                1
        );

        List<CustomerServiceCategory> categories = classifier.classify("Bro, this product is crap");

        assertThat(categories).containsExactly(FEEDBACK_AND_COMPLAINTS);
    }

    @Test
    void should_classify_respecting_meanToMaxScoreRatio_0() {

        TextClassifier<CustomerServiceCategory> classifier = new EmbeddingModelTextClassifier<>(
                new AllMiniLmL6V2QuantizedEmbeddingModel(),
                examples,
                1,
                0,
                0
        );

        List<CustomerServiceCategory> categories = classifier.classify("Bro, this product is crap");

        assertThat(categories).containsExactly(RETURNS_AND_EXCHANGES);
    }
}