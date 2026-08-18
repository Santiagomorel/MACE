package com.company.rotations.spi;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NotificationChannelTest {

    @Test
    void shouldHaveSendMethod() {
        Method[] methods = NotificationChannel.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("send") &&
                m.getReturnType() == void.class &&
                m.getParameterCount() == 2 &&
                m.getParameterTypes()[0] == String.class &&
                m.getParameterTypes()[1] == Map.class) {
                found = true;
                break;
            }
        }
        assertTrue(found, "NotificationChannel must have send(String, Map) returning void");
    }

    @Test
    void shouldHaveVersionConstant() throws NoSuchFieldException {
        assertNotNull(NotificationChannel.class.getField("VERSION"));
        assertEquals("1.0.0", NotificationChannel.VERSION);
    }

    @Test
    void shouldHaveGetVersionDefaultMethod() {
        Method[] methods = NotificationChannel.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("getVersion") &&
                m.getReturnType() == String.class &&
                m.getParameterCount() == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "NotificationChannel must have getVersion() returning String");
    }
}
