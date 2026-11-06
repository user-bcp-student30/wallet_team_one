package pe.bcp.wallet.dto.mock;

public class MockKycCreateRequest {
    public String documentType;
    public String document;
    public String scenario;

    public MockKycCreateRequest() {
    }

    public MockKycCreateRequest(String documentType, String document, String scenario) {
        this.documentType = documentType;
        this.document = document;
        this.scenario = scenario;
    }
}
