package cl.duoc.cloudnative.inventory.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**")
                        .hasAnyAuthority("SCOPE_inventory.read", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products", "/api/v1/products/**")
                        .hasAnyAuthority("SCOPE_inventory.write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products", "/api/v1/products/**")
                        .hasAnyAuthority("SCOPE_inventory.write", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products", "/api/v1/products/**")
                        .hasAnyAuthority("SCOPE_inventory.write", "ROLE_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token JWT ausente, vencido o invalido"))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token JWT sin permisos suficientes"))
                )
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            SecurityProperties securityProperties
    ) {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> issuerValidator = this::validateIssuer;
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(securityProperties.getAudiences());
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                defaultValidator,
                issuerValidator,
                audienceValidator
        ));
        return jwtDecoder;
    }

    private OAuth2TokenValidatorResult validateIssuer(Jwt token) {
        List<String> allowedIssuers = List.of(
                "https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0",
                "https://sts.windows.net/94b15b07-9bad-4661-8554-aad0e62b18de/"
        );
        if (allowedIssuers.contains(token.getIssuer().toString())) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "El token JWT no contiene un issuer permitido",
                null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        addSpaceSeparatedAuthorities(authorities, jwt.getClaimAsString("scope"), "SCOPE_");
        addSpaceSeparatedAuthorities(authorities, jwt.getClaimAsString("scp"), "SCOPE_");
        addListAuthorities(authorities, jwt.getClaimAsStringList("roles"), "ROLE_");

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            roles.stream()
                    .map(Object::toString)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
        }
        return authorities;
    }

    private void addSpaceSeparatedAuthorities(List<GrantedAuthority> authorities, String claim, String prefix) {
        if (claim == null || claim.isBlank()) {
            return;
        }
        for (String value : claim.split(" ")) {
            authorities.add(new SimpleGrantedAuthority(prefix + value));
        }
    }

    private void addListAuthorities(List<GrantedAuthority> authorities, List<String> values, String prefix) {
        if (values == null) {
            return;
        }
        values.stream()
                .map(value -> new SimpleGrantedAuthority(prefix + value))
                .forEach(authorities::add);
    }
}
