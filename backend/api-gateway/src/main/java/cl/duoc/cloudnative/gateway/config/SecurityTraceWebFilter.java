package cl.duoc.cloudnative.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityTraceWebFilter implements WebFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityTraceWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        boolean authorizationPresent = exchange.getRequest().getHeaders().containsKey("Authorization");

        LOGGER.info("Security ingress: method={}, path={}, authorizationPresent={}",
                method, path, authorizationPresent);

        return chain.filter(exchange).doFinally(signal -> LOGGER.info(
                "Security egress: method={}, path={}, status={}",
                method,
                path,
                exchange.getResponse().getStatusCode()
        ));
    }
}
