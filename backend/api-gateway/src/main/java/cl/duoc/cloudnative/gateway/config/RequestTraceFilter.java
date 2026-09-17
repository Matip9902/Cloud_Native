package cl.duoc.cloudnative.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestTraceFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        boolean authorizationPresent = exchange.getRequest().getHeaders().containsKey("Authorization");

        LOGGER.info("Gateway request: method={}, path={}, authorizationPresent={}",
                method, path, authorizationPresent);

        return chain.filter(exchange).doFinally(signal -> LOGGER.info(
                "Gateway response: method={}, path={}, status={}",
                method,
                path,
                exchange.getResponse().getStatusCode()
        ));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
