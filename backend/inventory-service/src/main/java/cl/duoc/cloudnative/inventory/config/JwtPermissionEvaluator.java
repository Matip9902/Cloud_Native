package cl.duoc.cloudnative.inventory.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

@Component("jwtPermissionEvaluator")
public class JwtPermissionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtPermissionEvaluator.class);

    public boolean canWrite(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }

        Object scopeClaim = jwtAuthentication.getToken().getClaims().get("scp");
        if (scopeClaim == null) {
            scopeClaim = jwtAuthentication.getToken().getClaims().get("scope");
        }

        boolean hasWriteScope = containsValue(scopeClaim, "inventory.write");

        Object rolesClaim = jwtAuthentication.getToken().getClaims().get("roles");
        boolean isAdmin = containsValue(rolesClaim, "ADMIN");

        boolean permitted = hasWriteScope || isAdmin;
        LOGGER.info("Product write permission: scopes={}, roles={}, permitted={}",
                scopeClaim, rolesClaim, permitted);

        return permitted;
    }

    private boolean containsValue(Object claim, String requiredValue) {
        if (claim instanceof Collection<?> values) {
            return values.stream()
                    .map(Object::toString)
                    .anyMatch(value -> requiredValue.equalsIgnoreCase(value));
        }
        if (claim == null) {
            return false;
        }
        String normalized = claim.toString()
                .replace("[", "")
                .replace("]", "")
                .replace(",", " ")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(normalized.split("\\s+"))
                .anyMatch(requiredValue.toLowerCase(Locale.ROOT)::equals);
    }
}
