package com.company.rotations.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GenericAlertModelTest {

    private final ObjectMapper mapper = new ObjectMapper();

    GenericAlertModelTest() {
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateGenericAlertModel() {
        GenericAlertModel model = new GenericAlertModel();
        model.setEventId("evt-1");
        model.setSource("gitguardian");
        model.setSourceEventId("sg-1");
        model.setProviderSeverity("high");

        GenericAlertModel.DetectedSecret secret = new GenericAlertModel.DetectedSecret();
        secret.setType("aws_access_key");
        secret.setValueHash("abc123");
        secret.setPattern("AKIA");
        model.setDetectedSecret(secret);

        GenericAlertModel.AlertContext context = new GenericAlertModel.AlertContext();
        context.setRepository("my-repo");
        context.setFile("src/config.yml");
        context.setLine(42);
        context.setVisibility("public");
        model.setContext(context);

        GenericAlertModel.DetectorState detectorState = new GenericAlertModel.DetectorState();
        detectorState.setIsNew(true);
        detectorState.setPreviouslyFlagged(false);
        detectorState.setFlagCount(0);
        model.setDetectorState(detectorState);

        assertEquals("evt-1", model.getEventId());
        assertEquals("gitguardian", model.getSource());
        assertEquals("sg-1", model.getSourceEventId());
        assertEquals("high", model.getProviderSeverity());
        assertEquals("abc123", model.getValueHash());
        assertEquals("my-repo", model.getContext().getRepository());
        assertEquals(42, model.getContext().getLine().intValue());
        assertTrue(model.getDetectorState().isNew());
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        GenericAlertModel model = new GenericAlertModel();
        model.setEventId("evt-2");
        model.setSource("gitguardian");
        model.setSourceEventId("sg-2");

        GenericAlertModel.DetectedSecret secret = new GenericAlertModel.DetectedSecret();
        secret.setType("iam_user");
        secret.setValueHash("def456");
        model.setDetectedSecret(secret);

        String json = mapper.writeValueAsString(model);
        GenericAlertModel deserialized = mapper.readValue(json, GenericAlertModel.class);

        assertEquals("evt-2", deserialized.getEventId());
        assertEquals("gitguardian", deserialized.getSource());
        assertEquals("def456", deserialized.getValueHash());
    }

    @Test
    void shouldHandleMissingOptionalFields() throws Exception {
        GenericAlertModel model = new GenericAlertModel();
        model.setEventId("evt-3");
        model.setSource("unknown");

        String json = mapper.writeValueAsString(model);
        GenericAlertModel deserialized = mapper.readValue(json, GenericAlertModel.class);

        assertEquals("evt-3", deserialized.getEventId());
        assertNull(deserialized.getDetectedSecret());
        assertNull(deserialized.getValueHash());
        assertNull(deserialized.getContext());
    }

    @Test
    void shouldHandleRawPayload() {
        GenericAlertModel model = new GenericAlertModel();
        Map<String, Object> raw = Map.of("key", "value", "nested", Map.of("a", 1));
        model.setRawPayload(raw);

        assertEquals("value", model.getRawPayload().get("key"));
        assertEquals(1, ((Map) model.getRawPayload().get("nested")).get("a"));
    }
}
