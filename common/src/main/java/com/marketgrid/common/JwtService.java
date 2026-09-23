package com.marketgrid.common;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import io.jsonwebtoken.Claims;
import javax.crypto.SecretKey;

public class JwtService {
    public static final String SECRET = "marketgrid-development-secret-key-change-me-32chars";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public String issue(String subject, String role, String tenantId, String vendorId) {
        var builder = Jwts.builder().subject(subject).claim("role", role).claim("tenantId", tenantId).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 86_400_000));
        if (vendorId != null) builder.claim("vendorId", vendorId);
        return builder.signWith(key).compact();
    }

    public boolean valid(String token) {
        try { claims(token); return true; }
        catch (Exception ignored) { return false; }
    }

    public Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
