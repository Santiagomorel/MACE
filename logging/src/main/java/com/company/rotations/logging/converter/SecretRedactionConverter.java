package com.company.rotations.logging.converter;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecretRedactionConverter extends ClassicConverter {

    private static final Pattern[] SECRET_PATTERNS = {
        Pattern.compile("(?i)(?:secret)\\s*[:=]\\s*(\\S+)"),
        Pattern.compile("(?i)(?:password)\\s*[:=]\\s*(\\S+)"),
        Pattern.compile("(?i)(?:api[_-]?key)\\s*[:=]\\s*(\\S+)"),
        Pattern.compile("(?i)(?:access[_-]?key)\\s*[:=]\\s*(\\S+)"),
        Pattern.compile("(?i)(?:private[_-]?key)\\s*[:=]\\s*(\\S+)"),
        Pattern.compile("(?i)(?:token)\\s*[:=]\\s*(\\S+)"),
        Pattern.compile("(?i)(?:key)\\s*[:=]\\s*(\\S+)")
    };

    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final int MIN_SECRET_LENGTH = 20;

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return null;
        }
        return redactSecrets(message);
    }

    public String redactSecrets(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = message;
        for (Pattern pattern : SECRET_PATTERNS) {
            result = applyPattern(result, pattern);
        }
        return result;
    }

    private String applyPattern(String input, Pattern pattern) {
        var matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String secretValue = matcher.group(1);
            String replacement = secretValue.length() >= MIN_SECRET_LENGTH
                ? REDACTED_VALUE
                : secretValue;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
