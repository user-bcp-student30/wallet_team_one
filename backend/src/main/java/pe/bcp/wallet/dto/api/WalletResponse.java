package pe.bcp.wallet.dto.api;

public class WalletResponse {
    public String walletId;
    public String status;
    public long balanceCents;
    public long reservedCents;
    public long availableCents;
    public String currency;
}
