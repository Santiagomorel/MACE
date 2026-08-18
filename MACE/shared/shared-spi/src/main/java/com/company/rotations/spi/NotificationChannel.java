package com.company.rotations.spi;

import java.util.Map;

public interface NotificationChannel {
    String VERSION = "1.0.0";

    void send(String message, Map<String, String> context);

    default String getVersion() {
        return VERSION;
    }
}
