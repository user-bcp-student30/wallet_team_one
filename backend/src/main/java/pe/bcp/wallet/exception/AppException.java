package pe.bcp.wallet.exception;

/**
 * Error de negocio/validación propio del BFF. Se traduce 1:1 al contrato de
 * error unificado de CONTRACT.md.
 */
public class AppException extends RuntimeException {

    private final int httpStatus;
    private final String code;
    private final boolean retryable;

    public AppException(int httpStatus, String code, String message, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.retryable = retryable;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
