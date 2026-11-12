package com.xiye.garden.auth.security;

import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

public record GardenPrincipal(long userId, int credentialVersion) implements AuthenticatedPrincipal, Serializable {
    @Override
    public String getName() { return Long.toString(userId); }
}
