package com.company.rotations.alerting.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/alerts");
    }

    @Nested
    @DisplayName("Business Exception Handling")
    class BusinessExceptionTests {

        @Test
        @DisplayName("Should return 400 with business error details")
        void shouldReturn400ForBusinessException() {
            BusinessException exception = new BusinessException("Credential type not supported");

            ResponseEntity<ErrorResponse> response = handler.handleBusinessException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().status());
            assertEquals("BUSINESS_ERROR", response.getBody().error());
            assertEquals("/api/v1/alerts", response.getBody().path());
            assertEquals("Credential type not supported", response.getBody().message());
            assertTrue(response.getBody().details().isEmpty());
        }

        @Test
        @DisplayName("Should return 400 with custom error code")
        void shouldReturn400WithCustomErrorCode() {
            BusinessException exception = new BusinessException("Invalid tenant", "INVALID_TENANT");

            ResponseEntity<ErrorResponse> response = handler.handleBusinessException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("INVALID_TENANT", response.getBody().error());
        }
    }

    @Nested
    @DisplayName("Validation Exception Handling")
    class ValidationExceptionTests {

        @Test
        @DisplayName("Should return 400 with field-specific validation error")
        void shouldReturn400ForFieldValidationException() {
            ValidationException exception = new ValidationException("credentialType", "must be one of: AWS_ACCESS_KEY, IAM_USER");

            ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("VALIDATION_ERROR", response.getBody().error());
            assertEquals(1, response.getBody().details().size());
            assertTrue(response.getBody().details().get(0).contains("credentialType"));
        }

        @Test
        @DisplayName("Should return 400 with generic validation error")
        void shouldReturn400ForGenericValidationException() {
            ValidationException exception = new ValidationException("Invalid payload format");

            ResponseEntity<ErrorResponse> response = handler.handleValidationException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("VALIDATION_ERROR", response.getBody().error());
            assertFalse(response.getBody().details().get(0).contains("Field"));
        }
    }

    @Nested
    @DisplayName("Method Argument Not Valid Exception Handling")
    class MethodArgumentNotValidTests {

        @Test
        @DisplayName("Should return 400 with all validation field errors")
        void shouldReturn400WithFieldErrors() {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "alertRequest");
            bindingResult.addError(new FieldError("alertRequest", "providerName", "must not be blank"));
            bindingResult.addError(new FieldError("alertRequest", "tenantId", "must not be blank"));
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

            ResponseEntity<ErrorResponse> response = handler.handleValidationMethodException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(2, response.getBody().details().size());
            assertTrue(response.getBody().details().get(0).contains("providerName"));
            assertTrue(response.getBody().details().get(1).contains("tenantId"));
        }

        @Test
        @DisplayName("Should return 400 with no field errors")
        void shouldReturn400WithNoFieldErrors() {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "alertRequest");
            MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

            ResponseEntity<ErrorResponse> response = handler.handleValidationMethodException(exception, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().status());
        }
    }

    @Nested
    @DisplayName("Technical Exception Handling")
    class TechnicalExceptionTests {

        @Test
        @DisplayName("Should return 500 without exposing internal details")
        void shouldReturn500WithoutStacktrace() {
            TechnicalException exception = new TechnicalException("Database connection failed");

            ResponseEntity<ErrorResponse> response = handler.handleTechnicalException(exception, request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(500, response.getBody().status());
            assertEquals("TECHNICAL_ERROR", response.getBody().error());
            assertEquals("An unexpected error occurred", response.getBody().message());
            assertTrue(response.getBody().details().isEmpty());
        }

        @Test
        @DisplayName("Should not expose stack trace details in error response")
        void shouldNotExposeStacktraceInResponse() {
            TechnicalException exception = new TechnicalException("NullPointerException at com.company.rotations.alerting.SomeClass.method(SomeClass.java:42)");

            ResponseEntity<ErrorResponse> response = handler.handleTechnicalException(exception, request);

            assertFalse(response.getBody().message().contains("SomeClass"));
            assertFalse(response.getBody().message().contains("method"));
        }
    }

    @Nested
    @DisplayName("Generic Exception Handling")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should return 500 for unhandled exceptions")
        void shouldReturn500ForUnhandledException() {
            RuntimeException exception = new RuntimeException("Something unexpected happened");

            ResponseEntity<ErrorResponse> response = handler.handleGenericException(exception, request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(500, response.getBody().status());
            assertEquals("INTERNAL_ERROR", response.getBody().error());
            assertEquals("An unexpected error occurred", response.getBody().message());
        }

        @Test
        @DisplayName("Should return 500 for null message exception")
        void shouldReturn500ForNullMessageException() {
            NullPointerException exception = new NullPointerException();

            ResponseEntity<ErrorResponse> response = handler.handleGenericException(exception, request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(500, response.getBody().status());
        }
    }
}
