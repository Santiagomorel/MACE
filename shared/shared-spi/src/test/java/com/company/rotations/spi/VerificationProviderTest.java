package com.company.rotations.spi;

import com.company.rotations.models.VerificationResult;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VerificationProviderTest {

    @Test
    void shouldHaveVerifyMethod() {
        Method[] methods = VerificationProvider.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("verify") &&
                m.getReturnType() == VerificationResult.class &&
                m.getParameterCount() == 3 &&
                m.getParameterTypes()[0] == String.class &&
                m.getParameterTypes()[1] == Map.class &&
                m.getParameterTypes()[2] == String.class) {
                found = true;
                break;
            }
        }
        assertTrue(found, "VerificationProvider must have verify(String, Map, String)");
    }

    @Test
    void shouldHaveVersionConstant() throws NoSuchFieldException {
        assertNotNull(VerificationProvider.class.getField("VERSION"));
        assertEquals("1.0.0", VerificationProvider.VERSION);
    }

    @Test
    void shouldHaveGetVersionDefaultMethod() {
        Method[] methods = VerificationProvider.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("getVersion") &&
                m.getReturnType() == String.class &&
                m.getParameterCount() == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "VerificationProvider must have getVersion() returning String");
    }
}
