package com.company.rotations.alerting.dlq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AlertDLQEntryTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create entry with correct values")
        void shouldCreateWithCorrectValues() {
            AlertDLQEntry entry = new AlertDLQEntry(
                    "{\"key\":\"value\"}",
                    "Connection timeout",
                    "gitguardian",
                    "evt-123",
                    "pipeline_error"
            );

            assertEquals("{\"key\":\"value\"}", entry.getRawPayload());
            assertEquals("Connection timeout", entry.getErrorMessage());
            assertEquals("gitguardian", entry.getSource());
            assertEquals("evt-123", entry.getSourceEventId());
            assertEquals(0, entry.getRetryCount());
            assertNotNull(entry.getCreatedAt());
            assertEquals(AlertDLQEntry.DLQStatus.PENDING, entry.getStatus());
            assertEquals("pipeline_error", entry.getAlertType());
        }

        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            AlertDLQEntry entry = new AlertDLQEntry(
                    null, null, null, null, null
            );

            assertNull(entry.getRawPayload());
            assertNull(entry.getErrorMessage());
            assertNull(entry.getSource());
            assertNull(entry.getSourceEventId());
            assertNull(entry.getAlertType());
            assertEquals(AlertDLQEntry.DLQStatus.PENDING, entry.getStatus());
            assertEquals(0, entry.getRetryCount());
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("Should update all fields via setters")
        void shouldUpdateFields() {
            AlertDLQEntry entry = new AlertDLQEntry("payload", "error", "src", "evt", "type");
            Instant customTime = Instant.parse("2024-01-01T00:00:00Z");

            entry.setId("custom-id");
            entry.setRawPayload("new-payload");
            entry.setErrorMessage("new-error");
            entry.setSource("new-source");
            entry.setSourceEventId("new-evt");
            entry.setCreatedAt(customTime);
            entry.setStatus(AlertDLQEntry.DLQStatus.RETRYING);
            entry.setAlertType("new-type");

            assertEquals("custom-id", entry.getId());
            assertEquals("new-payload", entry.getRawPayload());
            assertEquals("new-error", entry.getErrorMessage());
            assertEquals("new-source", entry.getSource());
            assertEquals("new-evt", entry.getSourceEventId());
            assertEquals(customTime, entry.getCreatedAt());
            assertEquals(AlertDLQEntry.DLQStatus.RETRYING, entry.getStatus());
            assertEquals("new-type", entry.getAlertType());
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryTests {

        @Test
        @DisplayName("Should increment retry count")
        void shouldIncrementRetry() {
            AlertDLQEntry entry = new AlertDLQEntry("p", "e", "s", "evt", "t");
            entry.incrementRetry();
            entry.incrementRetry();
            entry.incrementRetry();

            assertEquals(3, entry.getRetryCount());
        }

        @Test
        @DisplayName("Should not be at max retries initially")
        void shouldNotBeAtMaxInitially() {
            AlertDLQEntry entry = new AlertDLQEntry("p", "e", "s", "evt", "t");
            assertFalse(entry.isMaxRetries(3));
        }

        @Test
        @DisplayName("Should be at max retries when count >= max")
        void shouldBeAtMaxWhenCountExceeds() {
            AlertDLQEntry entry = new AlertDLQEntry("p", "e", "s", "evt", "t");
            entry.setRetryCount(5);
            assertTrue(entry.isMaxRetries(3));
        }

        @Test
        @DisplayName("Should not be at max retries when count < max")
        void shouldNotBeAtMaxWhenBelow() {
            AlertDLQEntry entry = new AlertDLQEntry("p", "e", "s", "evt", "t");
            entry.setRetryCount(2);
            assertFalse(entry.isMaxRetries(3));
        }

        @Test
        @DisplayName("Should be at max retries when count == max")
        void shouldBeAtMaxWhenEqual() {
            AlertDLQEntry entry = new AlertDLQEntry("p", "e", "s", "evt", "t");
            entry.setRetryCount(3);
            assertTrue(entry.isMaxRetries(3));
        }
    }

    @Nested
    @DisplayName("DLQ Status Enum")
    class StatusEnumTests {

        @Test
        @DisplayName("Should have expected status values")
        void shouldHaveExpectedValues() {
            assertEquals(3, AlertDLQEntry.DLQStatus.values().length);
            assertNotNull(AlertDLQEntry.DLQStatus.PENDING);
            assertNotNull(AlertDLQEntry.DLQStatus.RETRYING);
            assertNotNull(AlertDLQEntry.DLQStatus.ARCHIVED);
        }

        @Test
        @DisplayName("Should set and get status")
        void shouldSetAndGetStatus() {
            AlertDLQEntry entry = new AlertDLQEntry("p", "e", "s", "evt", "t");
            entry.setStatus(AlertDLQEntry.DLQStatus.ARCHIVED);
            assertEquals(AlertDLQEntry.DLQStatus.ARCHIVED, entry.getStatus());
        }
    }

    @Nested
    @DisplayName("No-Arg Constructor")
    class NoArgConstructorTests {

        @Test
        @DisplayName("Should create entry with all null/zero values")
        void shouldCreateWithNulls() {
            AlertDLQEntry entry = new AlertDLQEntry();
            assertNull(entry.getId());
            assertNull(entry.getRawPayload());
            assertNull(entry.getErrorMessage());
            assertNull(entry.getSource());
            assertNull(entry.getSourceEventId());
            assertEquals(0, entry.getRetryCount());
            assertNull(entry.getCreatedAt());
            assertNull(entry.getStatus());
            assertNull(entry.getAlertType());
        }
    }
}
