package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AlertTypeTest {

    @Test
    void shouldReturnAllEnumValues() {
        AlertType[] values = AlertType.values();
        assertEquals(4, values.length);
        assertEquals(AlertType.AWS_ACCESS_KEY, values[0]);
        assertEquals(AlertType.IAM_USER, values[1]);
        assertEquals(AlertType.RDS_CREDENTIAL, values[2]);
        assertEquals(AlertType.GENERIC, values[3]);
    }

    @Test
    void shouldValueOfReturnCorrectEnum() {
        assertEquals(AlertType.AWS_ACCESS_KEY, AlertType.valueOf("AWS_ACCESS_KEY"));
        assertEquals(AlertType.IAM_USER, AlertType.valueOf("IAM_USER"));
        assertEquals(AlertType.RDS_CREDENTIAL, AlertType.valueOf("RDS_CREDENTIAL"));
        assertEquals(AlertType.GENERIC, AlertType.valueOf("GENERIC"));
    }
}
