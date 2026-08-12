package com.company.rotations.spi;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PlaybookManagerTest {

    @Test
    void shouldHaveLoadPlaybookMethod() {
        Method[] methods = PlaybookManager.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("loadPlaybook") &&
                m.getReturnType() == Playbook.class &&
                m.getParameterCount() == 1 &&
                m.getParameterTypes()[0] == String.class) {
                found = true;
                break;
            }
        }
        assertTrue(found, "PlaybookManager must have loadPlaybook(String) returning Playbook");
    }

    @Test
    void shouldHaveGetPlaybookStepsMethod() {
        Method[] methods = PlaybookManager.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("getPlaybookSteps") &&
                m.getReturnType() == List.class &&
                m.getParameterCount() == 1 &&
                m.getParameterTypes()[0] == String.class) {
                found = true;
                break;
            }
        }
        assertTrue(found, "PlaybookManager must have getPlaybookSteps(String) returning List<String>");
    }

    @Test
    void shouldHaveVersionConstant() throws NoSuchFieldException {
        assertNotNull(PlaybookManager.class.getField("VERSION"));
        assertEquals("1.0.0", PlaybookManager.VERSION);
    }

    @Test
    void shouldHaveGetVersionDefaultMethod() {
        Method[] methods = PlaybookManager.class.getMethods();
        boolean found = false;
        for (Method m : methods) {
            if (m.getName().equals("getVersion") &&
                m.getReturnType() == String.class &&
                m.getParameterCount() == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "PlaybookManager must have getVersion() returning String");
    }

    @Test
    void shouldDefinePlaybookInnerClass() {
        boolean found = false;
        for (Class<?> inner : PlaybookManager.class.getClasses()) {
            if (inner.getSimpleName().equals("Playbook")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "PlaybookManager must define inner class Playbook");
    }
}
