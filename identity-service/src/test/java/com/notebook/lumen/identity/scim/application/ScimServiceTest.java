package com.notebook.lumen.identity.scim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.api.ScimPatchRequest;
import com.notebook.lumen.identity.scim.api.ScimUserRequest;
import com.notebook.lumen.identity.scim.domain.ScimMemberType;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupMembershipRepository;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupRepository;
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
    ScimGroupMembershipRepository memberships = mock(ScimGroupMembershipRepository.class);
    ScimGroupRepository groups = mock(ScimGroupRepository.class);
    ScimGroupGraphValidation graphValidation = mock(ScimGroupGraphValidation.class);
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
            ScimProperties.withLegacyDefaults(
                true, "token", "", true, "notebook-admins", true, 5, false, 100, 10),
            graphValidation);

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
    ScimGroupMembershipRepository memberships = mock(ScimGroupMembershipRepository.class);
    ScimGroupRepository groups = mock(ScimGroupRepository.class);
    ScimGroupGraphValidation graphValidation = mock(ScimGroupGraphValidation.class);
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
    when(memberships.findByMemberTypeAndMemberUser_Id(eq(ScimMemberType.USER), any()))
        .thenReturn(List.of());
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
            ScimProperties.withLegacyDefaults(
                true, "token", "", true, "notebook-admins", true, 5, false, 100, 10),
            graphValidation);

    var patched =
        service.patchUser(
            user.getId(),
            new ScimPatchRequest(
                List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                List.of(
                    new ScimPatchRequest.Operation("replace", "active", Map.of("active", false)))),
            mock(HttpServletRequest.class));

    assertThat(patched.active()).isFalse();
    assertThat(user.getDeprovisionedAt()).isNotNull();
    assertThat(user.getDeprovisionReason()).isEqualTo("SCIM_ACTIVE_FALSE_OR_DELETE");
    assertThat(user.getLastScimExternalId()).isEqualTo("ext-2");
  }

  @Test
  void createUserReactivatesDeprovisionedExternalId() {
    UserRepository userRepository = mock(UserRepository.class);
    RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    ScimGroupMembershipRepository memberships = mock(ScimGroupMembershipRepository.class);
    ScimGroupRepository groups = mock(ScimGroupRepository.class);
    ScimGroupGraphValidation graphValidation = mock(ScimGroupGraphValidation.class);
    AuditService auditService = mock(AuditService.class);
    User existing =
        new User(
            UUID.randomUUID(),
            "old@example.com",
            "Old User",
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
            "ext-reactivate",
            null);
    existing.deactivateByScim(Instant.now());
    when(userRepository.findByScimExternalId("ext-reactivate")).thenReturn(Optional.of(existing));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberships.findByMemberTypeAndMemberUser_Id(eq(ScimMemberType.USER), any()))
        .thenReturn(List.of());

    ScimService service =
        new ScimService(
            userRepository,
            refreshTokenRepository,
            memberships,
            groups,
            mock(PasswordEncoder.class),
            auditService,
            ScimProperties.withLegacyDefaults(
                true, "token", "", true, "notebook-admins", true, 5, false, 100, 10),
            graphValidation);

    var response =
        service.createUser(
            new ScimUserRequest(
                "new@example.com",
                null,
                "Reactivated User",
                List.of(new ScimUserRequest.Email("new@example.com", "work", true)),
                true,
                "ext-reactivate",
                List.of()),
            mock(HttpServletRequest.class));

    assertThat(response.active()).isTrue();
    assertThat(response.userName()).isEqualTo("new@example.com");
    assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(existing.getReactivatedAt()).isNotNull();
    assertThat(existing.getDeprovisionedAt()).isNull();
    verify(auditService)
        .record(eq("SCIM_USER_REACTIVATED"), any(), eq("USER"), eq(existing.getId()), any(), any());
  }

  @Test
  void putUserRejectsDuplicateExternalId() {
    UserRepository userRepository = mock(UserRepository.class);
    User current =
        new User(
            UUID.randomUUID(),
            "current@example.com",
            "Current User",
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
            "ext-current",
            null);
    User other =
        new User(
            UUID.randomUUID(),
            "other@example.com",
            "Other User",
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
            "ext-other",
            null);
    when(userRepository.findById(current.getId())).thenReturn(Optional.of(current));
    when(userRepository.findByScimExternalId("ext-other")).thenReturn(Optional.of(other));

    ScimService service =
        new ScimService(
            userRepository,
            mock(RefreshTokenRepository.class),
            mock(ScimGroupMembershipRepository.class),
            mock(ScimGroupRepository.class),
            mock(PasswordEncoder.class),
            mock(AuditService.class),
            ScimProperties.withLegacyDefaults(
                true, "token", "", true, "notebook-admins", true, 5, false, 100, 10),
            mock(ScimGroupGraphValidation.class));

    assertThatThrownBy(
            () ->
                service.putUser(
                    current.getId(),
                    new ScimUserRequest(
                        "current@example.com",
                        null,
                        "Current User",
                        List.of(new ScimUserRequest.Email("current@example.com", "work", true)),
                        true,
                        "ext-other",
                        List.of()),
                    mock(HttpServletRequest.class)))
        .isInstanceOf(ScimException.class)
        .satisfies(
            ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void unsupportedFilterThrowsInvalidFilter() {
    ScimService service =
        new ScimService(
            mock(UserRepository.class),
            mock(RefreshTokenRepository.class),
            mock(ScimGroupMembershipRepository.class),
            mock(ScimGroupRepository.class),
            mock(PasswordEncoder.class),
            mock(AuditService.class),
            ScimProperties.withLegacyDefaults(
                true, "token", "", true, "notebook-admins", true, 5, false, 100, 10),
            mock(ScimGroupGraphValidation.class));
    assertThatThrownBy(() -> service.listUsers(1, 10, "title co \"x\""))
        .isInstanceOf(ScimException.class)
        .satisfies(
            ex -> assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
