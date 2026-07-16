package com.littlebluenote.user.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterBodyValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void registrationRejectsChineseUsername() {
        RegisterBody body = new RegisterBody("测试user", "Valid display name", "password123");

        assertTrue(validator.validate(body).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("username")));
    }

    @Test
    void registrationAcceptsAsciiUsername() {
        RegisterBody body = new RegisterBody("alice_01", "Alice", "password123");

        assertTrue(validator.validate(body).isEmpty());
    }
}
