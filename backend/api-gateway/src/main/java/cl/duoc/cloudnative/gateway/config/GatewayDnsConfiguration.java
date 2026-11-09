package cl.duoc.cloudnative.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class GatewayDnsConfiguration {
    @Bean
    @Order(0)
    HttpClientCustomizer refreshServiceAddresses(
            @Value("${app.gateway.dns-cache-ttl-seconds:5}") int ttlSeconds) {
        if (ttlSeconds < 0) throw new IllegalArgumentException("Gateway DNS TTL must not be negative");
        // Docker may assign another IP to a restarted/recreated service. Never keep the old
        // positive answer indefinitely or cache a temporary NXDOMAIN during its startup.
        return client -> client.resolver(spec -> spec.cacheMinTimeToLive(Duration.ZERO)
                .cacheMaxTimeToLive(Duration.ofSeconds(ttlSeconds))
                .cacheNegativeTimeToLive(Duration.ZERO));
    }
}
