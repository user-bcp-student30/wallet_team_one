package pe.bcp.wallet.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import pe.bcp.wallet.dto.api.WalletResponse;
import pe.bcp.wallet.service.WalletService;

@Path("/api/v1/wallets")
@Produces(MediaType.APPLICATION_JSON)
public class WalletResource {

    @Inject
    WalletService service;

    @GET
    @Path("/{walletId}")
    public WalletResponse get(@PathParam("walletId") String walletId) {
        return service.getWallet(walletId);
    }
}
