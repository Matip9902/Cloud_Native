package cl.duoc.cloudnative.inventory.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> allowedAudiences;

    public AudienceValidator(List<String> allowedAudiences) {
        this.allowedAudiences = allowedAudiences;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (allowedAudiences == null || allowedAudiences.isEmpty()) {
            return OAuth2TokenValidatorResult.success();
        }
        boolean hasExpectedAudience = token.getAudience().stream().anyMatch(allowedAudiences::contains);
        if (hasExpectedAudience) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "El token JWT no contiene una audiencia permitida",
                null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }
}
