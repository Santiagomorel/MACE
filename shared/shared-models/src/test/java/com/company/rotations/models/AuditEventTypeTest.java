package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AuditEventTypeTest {

    @Test
    void shouldReturnAllEnumValues() {
        AuditEventType[] values = AuditEventType.values();
        assertEquals(9, values.length);
        assertEquals(AuditEventType.ALERT_INGESTED, values[0]);
        assertEquals(AuditEventType.ALERT_DEDUPLICATED, values[1]);
        assertEquals(AuditEventType.CREDENTIAL_VERIFIED, values[2]);
        assertEquals(AuditEventType.CREDENTIAL_EXPIRED, values[3]);
        assertEquals(AuditEventType.ROTATION_STARTED, values[4]);
        assertEquals(AuditEventType.ROTATION_COMPLETED, values[5]);
        assertEquals(AuditEventType.ROTATION_FAILED, values[6]);
        assertEquals(AuditEventType.ESCALATION_TRIGGERED, values[7]);
        assertEquals(AuditEventType.CREDENTIAL_ACCESSED, values[8]);
    }
}
