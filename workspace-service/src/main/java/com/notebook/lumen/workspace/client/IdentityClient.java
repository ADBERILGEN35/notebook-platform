package com.notebook.lumen.workspace.client;

import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface IdentityClient {
  @GetExchange("/internal/users/lookup")
  Optional<IdentityUserResponse> findByEmail(@RequestParam String email);

  record IdentityUserResponse(UUID id, String email) {}
}
