package com.company.rotations.alerting.validation;

import com.company.rotations.alerting.config.ValidationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

public class ValidEnumValidator implements ConstraintValidator<ValidEnum, String> {

    private String enumClassName;
    private boolean ignoreCase;

    @Override
    public void initialize(ValidEnum annotation) {
        this.enumClassName = annotation.enumClass();
        this.ignoreCase = Boolean.parseBoolean(annotation.ignoreCase());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            Class<?> enumClass = Class.forName(enumClassName);
            Object[] enumConstants = enumClass.getEnumConstants();
            String normalizedValue = ignoreCase ? value.toUpperCase() : value;

            return Arrays.stream(enumConstants)
                .map(e -> ignoreCase ? e.toString().toUpperCase() : e.toString())
                .anyMatch(s -> s.equals(normalizedValue));
        } catch (ClassNotFoundException e) {
            throw new ValidationException("Enum class not found: " + enumClassName);
        }
    }
}
