package com.notebook.lumen.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvitationTokenServiceTest {

  private final InvitationTokenService tokenService = new InvitationTokenService();

  @Test
  void hash_isStableAndDoesNotReturnPlaintext() {
    String token = "plain-token";

    String hash1 = tokenService.hash(token);
    String hash2 = tokenService.hash(token);

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).isNotEqualTo(token);
    assertThat(hash1).hasSize(64);
  }

  @Test
  void generatedTokens_areOpaque() {
    assertThat(tokenService.generatePlaintextToken()).hasSizeGreaterThan(30);
    assertThat(tokenService.generatePlaintextToken())
        .isNotEqualTo(tokenService.generatePlaintextToken());
  }
}
