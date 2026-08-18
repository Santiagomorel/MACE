package com.company.rotations.spi;

import com.company.rotations.models.RotationAction;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RotationServiceTest {

    @Test
    void shouldHaveRotateMethod() {
        Method[] methods = RotationService.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("rotate") &&
                m.getReturnType() == RotationAction.class &&
                m.getParameterCount() == 3 &&
                m.getParameterTypes()[0] == String.class &&
                m.getParameterTypes()[1] == Map.class &&
                m.getParameterTypes()[2] == String.class) {
                found = true;
                break;
            }
        }
        assertTrue(found, "RotationService must have rotate(String, Map, String)");
    }

    @Test
    void shouldHaveVersionConstant() throws NoSuchFieldException {
        assertNotNull(RotationService.class.getField("VERSION"));
        assertEquals("1.0.0", RotationService.VERSION);
    }

    @Test
    void shouldHaveGetVersionDefaultMethod() {
        Method[] methods = RotationService.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("getVersion") &&
                m.getReturnType() == String.class &&
                m.getParameterCount() == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "RotationService must have getVersion() returning String");
    }
}
