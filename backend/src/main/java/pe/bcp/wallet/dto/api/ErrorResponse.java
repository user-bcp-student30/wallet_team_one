package pe.bcp.wallet.dto.api;

public class ErrorResponse {
    public String code;
    public String message;
    public boolean retryable;
    public String traceId;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message, boolean retryable, String traceId) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
        this.traceId = traceId;
    }
}
