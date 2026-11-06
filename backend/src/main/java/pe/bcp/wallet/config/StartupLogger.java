package pe.bcp.wallet.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupLogger {

    private static final Logger LOG = Logger.getLogger(StartupLogger.class);

    @ConfigProperty(name = "mock.base-url")
    String mockBaseUrl;

    void onStart(@Observes StartupEvent event) {
        LOG.infof("wallet-backend iniciado. MOCK_BASE_URL=%s", mockBaseUrl);
    }
}
