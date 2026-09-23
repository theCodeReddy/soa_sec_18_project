package com.marketgrid.common;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  @Test void issuedTokenIsAcceptedByAnotherServiceInstance() {
    var issuer = new JwtService();
    var verifier = new JwtService();
    assertTrue(verifier.valid(issuer.issue("customer", "CUSTOMER", "tenant-a", null)));
  }
}
