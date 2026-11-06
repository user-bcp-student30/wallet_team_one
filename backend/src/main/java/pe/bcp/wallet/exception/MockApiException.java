package pe.bcp.wallet.exception;

/** Error devuelto por el mock externo (status HTTP + código "error"). */
public class MockApiException extends RuntimeException {

    private final int httpStatus;
    private final String mockCode;

    public MockApiException(int httpStatus, String mockCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.mockCode = mockCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMockCode() {
        return mockCode;
    }
}
