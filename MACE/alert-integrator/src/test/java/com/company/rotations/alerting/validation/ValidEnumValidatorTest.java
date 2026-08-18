package com.company.rotations.alerting.validation;

import com.company.rotations.alerting.config.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidEnumValidatorTest {

    private ValidEnumValidator validator = new ValidEnumValidator();

    @Nested
    @DisplayName("Valid Values")
    class ValidValueTests {

        @Test
        @DisplayName("Should accept valid enum value with case-sensitive check")
        void shouldAcceptValidValueCaseSensitive() {
            ValidEnum annotation = mock(ValidEnum.class);
            when(annotation.enumClass()).thenReturn("com.company.rotations.models.AlertType");
            when(annotation.ignoreCase()).thenReturn("false");

            validator.initialize(annotation);

            assertTrue(validator.isValid("AWS_ACCESS_KEY", null));
        }

        @Test
        @DisplayName("Should accept valid enum value with case-insensitive check")
        void shouldAcceptValidValueCaseInsensitive() {
            ValidEnum annotation = mock(ValidEnum.class);
            when(annotation.enumClass()).thenReturn("com.company.rotations.models.AlertType");
            when(annotation.ignoreCase()).thenReturn("true");

            validator.initialize(annotation);

            assertTrue(validator.isValid("aws_access_key", null));
        }

        @Test
        @DisplayName("Should accept null value")
        void shouldAcceptNull() {
            ValidEnum annotation = mock(ValidEnum.class);
            when(annotation.enumClass()).thenReturn("com.company.rotations.models.AlertType");
            when(annotation.ignoreCase()).thenReturn("false");

            validator.initialize(annotation);
            assertTrue(validator.isValid(null, null));
        }

        @Test
        @DisplayName("Should accept blank value")
        void shouldAcceptBlank() {
            ValidEnum annotation = mock(ValidEnum.class);
            when(annotation.enumClass()).thenReturn("com.company.rotations.models.AlertType");
            when(annotation.ignoreCase()).thenReturn("false");

            validator.initialize(annotation);
            assertTrue(validator.isValid("   ", null));
        }
    }

    @Nested
    @DisplayName("Invalid Values")
    class InvalidValueTests {

        @Test
        @DisplayName("Should reject invalid enum value")
        void shouldRejectInvalidValue() {
            ValidEnum annotation = mock(ValidEnum.class);
            when(annotation.enumClass()).thenReturn("com.company.rotations.models.AlertType");
            when(annotation.ignoreCase()).thenReturn("false");

            validator.initialize(annotation);

            assertFalse(validator.isValid("INVALID_TYPE", null));
        }

        @Test
        @DisplayName("Should reject unknown class name")
        void shouldRejectUnknownClass() {
            ValidEnum annotation = mock(ValidEnum.class);
            when(annotation.enumClass()).thenReturn("com.company.rotations.models.NonExistentEnum");
            when(annotation.ignoreCase()).thenReturn("false");

            validator.initialize(annotation);

            assertThrows(ValidationException.class, () ->
                    validator.isValid("some-value", null));
        }
    }
}
