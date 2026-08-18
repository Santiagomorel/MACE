package com.company.rotations.spi;

import com.company.rotations.models.GenericAlertModel;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AlertAdapterTest {

    @Test
    void shouldHaveToGenericAlertMethod() {
        Method[] methods = AlertAdapter.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("toGenericAlert") &&
                m.getReturnType() == GenericAlertModel.class &&
                m.getParameterCount() == 1 &&
                m.getParameterTypes()[0] == Map.class) {
                found = true;
                break;
            }
        }
        assertTrue(found, "AlertAdapter must have toGenericAlert(Map<String, Object>) returning GenericAlertModel");
    }

    @Test
    void shouldHaveGetProviderNameMethod() {
        Method[] methods = AlertAdapter.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("getProviderName") &&
                m.getReturnType() == String.class &&
                m.getParameterCount() == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "AlertAdapter must have getProviderName() returning String");
    }

    @Test
    void shouldHaveVersionConstant() throws NoSuchFieldException {
        assertNotNull(AlertAdapter.class.getField("VERSION"));
        assertEquals("1.0.0", AlertAdapter.VERSION);
    }

    @Test
    void shouldHaveGetVersionDefaultMethod() {
        Method[] methods = AlertAdapter.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("getVersion") &&
                m.getReturnType() == String.class &&
                m.getParameterCount() == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "AlertAdapter must have getVersion() returning String");
    }
}
