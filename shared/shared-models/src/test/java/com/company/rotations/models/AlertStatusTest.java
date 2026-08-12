package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AlertStatusTest {

    @Test
    void shouldReturnAllEnumValues() {
        AlertStatus[] values = AlertStatus.values();
        assertEquals(8, values.length);
        assertEquals(AlertStatus.PENDING, values[0]);
        assertEquals(AlertStatus.PROCESSING, values[1]);
        assertEquals(AlertStatus.VERIFIED, values[2]);
        assertEquals(AlertStatus.DECIDED, values[3]);
        assertEquals(AlertStatus.ROTATING, values[4]);
        assertEquals(AlertStatus.COMPLETED, values[5]);
        assertEquals(AlertStatus.FAILED, values[6]);
        assertEquals(AlertStatus.IN_DLQ, values[7]);
    }

    @Test
    void shouldValueOfReturnCorrectEnum() {
        assertEquals(AlertStatus.PENDING, AlertStatus.valueOf("PENDING"));
        assertEquals(AlertStatus.COMPLETED, AlertStatus.valueOf("COMPLETED"));
    }
}
