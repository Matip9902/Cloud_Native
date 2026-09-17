package cl.duoc.cloudnative.inventory.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;

@Component("jwtPermissionEvaluator")
public class JwtPermissionEvaluator {

    public boolean canWrite(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }

        Object scopeClaim = jwtAuthentication.getToken().getClaims().get("scp");
        if (scopeClaim == null) {
            scopeClaim = jwtAuthentication.getToken().getClaims().get("scope");
        }

        boolean hasWriteScope = scopeClaim != null
                && Arrays.asList(scopeClaim.toString().split(" ")).contains("inventory.write");

        Object rolesClaim = jwtAuthentication.getToken().getClaims().get("roles");
        boolean isAdmin = rolesClaim instanceof Collection<?> roles
                && roles.stream().map(Object::toString).anyMatch("ADMIN"::equals);

        return hasWriteScope || isAdmin;
    }
}
