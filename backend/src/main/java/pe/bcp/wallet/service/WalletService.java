package pe.bcp.wallet.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pe.bcp.wallet.client.BcpWalletMockClient;
import pe.bcp.wallet.dto.api.WalletResponse;
import pe.bcp.wallet.dto.mock.MockWallet;

@ApplicationScoped
public class WalletService {

    @Inject
    @RestClient
    BcpWalletMockClient mockClient;

    public WalletResponse getWallet(String walletId) {
        MockWallet w = mockClient.getWallet(walletId);
        WalletResponse r = new WalletResponse();
        r.walletId = w.id;
        r.status = w.status;
        r.balanceCents = w.balance;
        r.reservedCents = w.reserved;
        r.availableCents = w.available;
        r.currency = w.currency;
        return r;
    }
}
