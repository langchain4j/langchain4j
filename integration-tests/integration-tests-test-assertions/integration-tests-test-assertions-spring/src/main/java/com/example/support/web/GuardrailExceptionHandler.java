package com.example.support.web;

import dev.langchain4j.guardrail.GuardrailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GuardrailExceptionHandler {

    @ExceptionHandler(GuardrailException.class)
    ResponseEntity<String> handle(GuardrailException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body("Sorry, I cannot answer that.");
    }
}
