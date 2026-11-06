package pe.bcp.wallet.util;

import pe.bcp.wallet.exception.AppException;

import java.time.Instant;

public final class Dates {
    private Dates() {
    }

    public static Instant parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new AppException(400, "VALIDATION_ERROR", "Fecha inválida, use formato ISO-8601: " + value, false);
        }
    }
}
