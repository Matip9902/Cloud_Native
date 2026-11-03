package cl.duoc.cloudnative.kafkaadmin;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health","/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/**").hasAnyAuthority("SCOPE_inventory.read","ROLE_ADMIN")
                .anyRequest().hasAuthority("ROLE_ADMIN"))
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(authenticationConverter()))).build();
    }
    private org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter authenticationConverter() {
        var converter=new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            java.util.List<org.springframework.security.core.GrantedAuthority> authorities=new java.util.ArrayList<>();
            String scopes=jwt.getClaimAsString("scp");
            if (scopes==null) scopes=jwt.getClaimAsString("scope");
            if (scopes!=null) for (String scope:scopes.split(" ")) authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_"+scope));
            var roles=jwt.getClaimAsStringList("roles");
            if (roles!=null) roles.forEach(role -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role)));
            return authorities;
        });
        return converter;
    }
    @Bean JwtDecoder decoder(
            @Value("${JWT_ISSUER_URI:https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0}") String issuer,
            @Value("${JWT_JWK_SET_URI:https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/discovery/v2.0/keys}") String jwks,
            @Value("${JWT_AUDIENCES:api://0f18b5b0-285b-4050-a959-245884ab7670,0f18b5b0-285b-4050-a959-245884ab7670}") String audiences) {
        var decoder=NimbusJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> audience=jwt -> jwt.getAudience().stream().anyMatch(java.util.List.of(audiences.split(","))::contains)
            ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid audience",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audience));
        return decoder;
    }
}
