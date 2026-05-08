package com.notebook.lumen.identity.scim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.api.ScimPatchRequest;
import com.notebook.lumen.identity.scim.api.ScimUserRequest;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupRepository;
import com.notebook.lumen.identity.scim.infrastructure.UserScimGroupMembershipRepository;
import com.notebook.lumen.identity.user.domain.RefreshToken;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserSource;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.RefreshTokenRepository;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class ScimServiceTest {

  @Test
  void createUserSetsScimSource() {
    UserRepository userRepository = mock(UserRepository.class);
    RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    UserScimGroupMembershipRepository memberships = mock(UserScimGroupMembershipRepository.class);
    ScimGroupRepository groups = mock(ScimGroupRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.encode(any())).thenReturn("pw");
    when(userRepository.findByEmail("scim@example.com")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    ScimService service =
        new ScimService(
            userRepository,
            refreshTokenRepository,
            memberships,
            groups,
            encoder,
            mock(AuditService.class),
            new ScimProperties(true, "token", "", true, "notebook-admins"));

    var result =
        service.createUser(
            new ScimUserRequest(
                "scim@example.com",
                null,
                "Scim User",
                List.of(new ScimUserRequest.Email("scim@example.com", "work", true)),
                true,
                "ext-1",
                List.of()),
            mock(HttpServletRequest.class));

    assertThat(result.userName()).isEqualTo("scim@example.com");
  }

  @Test
  void patchActiveFalseRevokesRefreshTokens() {
    UserRepository userRepository = mock(UserRepository.class);
    RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    UserScimGroupMembershipRepository memberships = mock(UserScimGroupMembershipRepository.class);
    ScimGroupRepository groups = mock(ScimGroupRepository.class);
    User user =
        new User(
            UUID.randomUUID(),
            "patch@example.com",
            "Patch User",
            null,
            "pw",
            UserStatus.ACTIVE,
            Instant.now(),
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            null,
            UserSource.SCIM,
            "ext-2",
            null);
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberships.findByUserId(user.getId())).thenReturn(List.of());
    when(refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(any(), any()))
        .thenReturn(
            List.of(
                new RefreshToken(
                    UUID.randomUUID(),
                    user,
                    "hash",
                    Instant.now().plusSeconds(600),
                    null,
                    null,
                    Instant.now(),
                    null,
                    null)));

    ScimService service =
        new ScimService(
            userRepository,
            refreshTokenRepository,
            memberships,
            groups,
            mock(PasswordEncoder.class),
            mock(AuditService.class),
            new ScimProperties(true, "token", "", true, "notebook-admins"));

    var patched =
        service.patchUser(
            user.getId(),
            new ScimPatchRequest(
                List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                List.of(new ScimPatchRequest.Operation("replace", "active", Map.of("active", false)))),
            mock(HttpServletRequest.class));

    assertThat(patched.active()).isFalse();
  }

  @Test
  void unsupportedFilterThrowsInvalidFilter() {
    ScimService service =
        new ScimService(
            mock(UserRepository.class),
            mock(RefreshTokenRepository.class),
            mock(UserScimGroupMembershipRepository.class),
            mock(ScimGroupRepository.class),
            mock(PasswordEncoder.class),
            mock(AuditService.class),
            new ScimProperties(true, "token", "", true, "notebook-admins"));
    assertThatThrownBy(() -> service.listUsers(1, 10, "title co \"x\""))
        .isInstanceOf(ScimException.class)
        .satisfies(ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
