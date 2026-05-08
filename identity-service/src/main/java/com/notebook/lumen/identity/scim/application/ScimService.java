package com.notebook.lumen.identity.scim.application;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.api.ScimGroupRequest;
import com.notebook.lumen.identity.scim.api.ScimGroupResponse;
import com.notebook.lumen.identity.scim.api.ScimListResponse;
import com.notebook.lumen.identity.scim.api.ScimPatchRequest;
import com.notebook.lumen.identity.scim.api.ScimUserRequest;
import com.notebook.lumen.identity.scim.api.ScimUserResponse;
import com.notebook.lumen.identity.scim.domain.ScimGroup;
import com.notebook.lumen.identity.scim.domain.UserScimGroupMembership;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupRepository;
import com.notebook.lumen.identity.scim.infrastructure.UserScimGroupMembershipRepository;
import com.notebook.lumen.identity.shared.security.EmailNormalizer;
import com.notebook.lumen.identity.user.domain.RefreshToken;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserSource;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.RefreshTokenRepository;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScimService {
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserScimGroupMembershipRepository membershipRepository;
  private final ScimGroupRepository scimGroupRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final ScimProperties properties;

  public ScimService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      UserScimGroupMembershipRepository membershipRepository,
      ScimGroupRepository scimGroupRepository,
      PasswordEncoder passwordEncoder,
      AuditService auditService,
      ScimProperties properties) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.membershipRepository = membershipRepository;
    this.scimGroupRepository = scimGroupRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public ScimListResponse<ScimUserResponse> listUsers(int startIndex, int count, String filter) {
    List<User> users;
    if (filter != null && !filter.isBlank()) {
      users = filteredUsers(filter);
    } else {
      int page = Math.max(0, (startIndex - 1) / Math.max(1, count));
      users =
          userRepository
              .findAllByOrderByCreatedAtAsc(PageRequest.of(page, Math.max(1, count)))
              .getContent();
    }
    List<ScimUserResponse> resources = users.stream().map(this::toScimUser).toList();
    return ScimListResponse.of(resources.size(), startIndex, count, resources);
  }

  @Transactional(readOnly = true)
  public ScimUserResponse getUser(UUID id) {
    return toScimUser(requireUser(id));
  }

  @Transactional
  public ScimUserResponse createUser(ScimUserRequest request, HttpServletRequest httpRequest) {
    String email = extractPrimaryEmail(request);
    String normalizedEmail = EmailNormalizer.normalize(email);
    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "User email already exists");
    }
    if (request.externalId() != null
        && !request.externalId().isBlank()
        && userRepository.findByScimExternalId(request.externalId()).isPresent()) {
      throw new ScimException(HttpStatus.CONFLICT, "uniqueness", "SCIM externalId already exists");
    }
    Instant now = Instant.now();
    User created =
        new User(
            UUID.randomUUID(),
            normalizedEmail,
            displayNameFrom(request),
            null,
            passwordEncoder.encode(UUID.randomUUID().toString()),
            request.active() == null || request.active() ? UserStatus.ACTIVE : UserStatus.DISABLED,
            now,
            null,
            now,
            now,
            now,
            null,
            UserSource.SCIM,
            blankToNull(request.externalId()),
            request.active() == null || request.active() ? null : now);
    userRepository.save(created);
    replaceMemberships(created, request.groups());
    auditService.record(
        "SCIM_USER_CREATED",
        null,
        "USER",
        created.getId(),
        httpRequest,
        Map.of("email", created.getEmail(), "externalId", Objects.toString(created.getScimExternalId(), "")));
    return toScimUser(created);
  }

  @Transactional
  public ScimUserResponse putUser(UUID id, ScimUserRequest request, HttpServletRequest httpRequest) {
    User user = requireUser(id);
    updateUserFields(user, request);
    userRepository.save(user);
    replaceMemberships(user, request.groups());
    auditService.record(
        "SCIM_USER_UPDATED",
        null,
        "USER",
        user.getId(),
        httpRequest,
        Map.of("email", user.getEmail()));
    return toScimUser(user);
  }

  @Transactional
  public ScimUserResponse patchUser(UUID id, ScimPatchRequest request, HttpServletRequest httpRequest) {
    User user = requireUser(id);
    for (ScimPatchRequest.Operation op : request.operations()) {
      if (!"replace".equalsIgnoreCase(op.op())) {
        throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "Only replace is supported");
      }
      String path = op.path() == null ? "" : op.path();
      if ("active".equalsIgnoreCase(path) || path.isBlank()) {
        Boolean active = asBoolean(op.value().get("active"));
        if (active != null) {
          applyActive(user, active, httpRequest);
        }
      } else if ("displayName".equalsIgnoreCase(path)) {
        user.setName(asString(op.value().get("displayName")));
      } else if ("emails".equalsIgnoreCase(path)) {
        List<ScimUserRequest.Email> emails = ScimPatchRequest.readEmails(op.value().get("emails"));
        user.setEmail(EmailNormalizer.normalize(emails.get(0).value()));
      } else if ("groups".equalsIgnoreCase(path)) {
        replaceMemberships(user, ScimPatchRequest.readGroups(op.value().get("groups")));
        auditService.record(
            "SCIM_USER_GROUPS_UPDATED",
            null,
            "USER",
            user.getId(),
            httpRequest,
            Map.of("groupsCount", membershipRepository.findByUserId(user.getId()).size()));
      } else {
        throw new ScimException(HttpStatus.BAD_REQUEST, "invalidPath", "Unsupported patch path: " + path);
      }
    }
    userRepository.save(user);
    auditService.record("SCIM_USER_UPDATED", null, "USER", user.getId(), httpRequest, Map.of());
    return toScimUser(user);
  }

  @Transactional
  public void deleteUser(UUID id, HttpServletRequest request) {
    User user = requireUser(id);
    applyActive(user, false, request);
    userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public ScimListResponse<ScimGroupResponse> listGroups(int startIndex, int count) {
    if (!properties.groupsEnabled()) {
      return ScimListResponse.of(0, startIndex, count, List.of());
    }
    int page = Math.max(0, (startIndex - 1) / Math.max(1, count));
    List<ScimGroupResponse> groups =
        scimGroupRepository.findAll(PageRequest.of(page, Math.max(1, count))).stream()
            .map(g -> new ScimGroupResponse(g.getId().toString(), g.getExternalId(), g.getDisplayName()))
            .toList();
    return ScimListResponse.of(groups.size(), startIndex, count, groups);
  }

  @Transactional
  public ScimGroupResponse upsertGroup(String idOrNull, ScimGroupRequest req, HttpServletRequest request) {
    if (!properties.groupsEnabled()) {
      throw new ScimException(HttpStatus.NOT_IMPLEMENTED, "invalidTarget", "SCIM groups disabled");
    }
    ScimGroup group =
        (idOrNull == null)
            ? scimGroupRepository
                .findByExternalId(req.externalId())
                .orElse(
                    new ScimGroup(
                        UUID.randomUUID(),
                        req.externalId(),
                        req.displayName(),
                        mapPlatformRole(req.displayName(), req.externalId()),
                        Instant.now(),
                        Instant.now()))
            : scimGroupRepository
                .findById(UUID.fromString(idOrNull))
                .orElseThrow(
                    () ->
                        new ScimException(HttpStatus.NOT_FOUND, "notFound", "Group not found"));
    boolean created = idOrNull == null && group.getCreatedAt().equals(group.getUpdatedAt());
    group.update(req.displayName(), mapPlatformRole(req.displayName(), req.externalId()));
    scimGroupRepository.save(group);
    auditService.record(
        created ? "SCIM_GROUP_CREATED" : "SCIM_GROUP_UPDATED",
        null,
        "SCIM_GROUP",
        group.getId(),
        request,
        Map.of("externalId", group.getExternalId(), "displayName", group.getDisplayName()));
    return new ScimGroupResponse(group.getId().toString(), group.getExternalId(), group.getDisplayName());
  }

  @Transactional
  public void deleteGroup(UUID id, HttpServletRequest request) {
    ScimGroup group =
        scimGroupRepository
            .findById(id)
            .orElseThrow(() -> new ScimException(HttpStatus.NOT_FOUND, "notFound", "Group not found"));
    scimGroupRepository.delete(group);
    auditService.record(
        "SCIM_GROUP_DELETED",
        null,
        "SCIM_GROUP",
        group.getId(),
        request,
        Map.of("externalId", group.getExternalId()));
  }

  private User requireUser(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new ScimException(HttpStatus.NOT_FOUND, "notFound", "User not found"));
  }

  private List<User> filteredUsers(String filter) {
    String trimmed = filter.trim();
    if (trimmed.startsWith("userName eq ")) {
      String value = parseFilterValue(trimmed);
      return userRepository.findByEmail(EmailNormalizer.normalize(value)).stream().toList();
    }
    if (trimmed.startsWith("emails.value eq ")) {
      String value = parseFilterValue(trimmed);
      return userRepository.findByEmail(EmailNormalizer.normalize(value)).stream().toList();
    }
    if (trimmed.startsWith("externalId eq ")) {
      String value = parseFilterValue(trimmed);
      return userRepository.findByScimExternalId(value).stream().toList();
    }
    throw new ScimException(HttpStatus.BAD_REQUEST, "invalidFilter", "Unsupported filter");
  }

  private String parseFilterValue(String filter) {
    int firstQuote = filter.indexOf('"');
    int lastQuote = filter.lastIndexOf('"');
    if (firstQuote < 0 || lastQuote <= firstQuote) {
      throw new ScimException(HttpStatus.BAD_REQUEST, "invalidFilter", "Malformed filter");
    }
    return filter.substring(firstQuote + 1, lastQuote);
  }

  private void updateUserFields(User user, ScimUserRequest request) {
    user.setName(displayNameFrom(request));
    user.setEmail(EmailNormalizer.normalize(extractPrimaryEmail(request)));
    user.setScimExternalId(blankToNull(request.externalId()));
    user.setSource(UserSource.SCIM);
    applyActive(user, request.active() == null || request.active(), null);
  }

  private void applyActive(User user, boolean active, HttpServletRequest request) {
    if (active) {
      user.reactivateByScim();
      if (request != null) {
        auditService.record("SCIM_USER_REACTIVATED", null, "USER", user.getId(), request, Map.of());
      }
      return;
    }
    user.deactivateByScim(Instant.now());
    List<RefreshToken> activeTokens =
        refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), Instant.now());
    Instant now = Instant.now();
    for (RefreshToken token : activeTokens) {
      token.revoke(now, null, "SCIM_DEPROVISION", null);
    }
    refreshTokenRepository.saveAll(activeTokens);
    if (request != null) {
      auditService.record(
          "SCIM_USER_DEPROVISIONED",
          null,
          "USER",
          user.getId(),
          request,
          Map.of("revokedRefreshTokens", activeTokens.size()));
      auditService.record(
          "USER_REFRESH_TOKENS_REVOKED_BY_SCIM",
          null,
          "USER",
          user.getId(),
          request,
          Map.of("count", activeTokens.size()));
    }
  }

  private void replaceMemberships(User user, List<ScimUserRequest.GroupRef> groups) {
    membershipRepository.deleteByUserId(user.getId());
    if (groups == null || groups.isEmpty()) {
      return;
    }
    List<UserScimGroupMembership> toCreate = new ArrayList<>();
    Instant now = Instant.now();
    for (ScimUserRequest.GroupRef group : groups) {
      if (group.value() == null || group.value().isBlank()) {
        continue;
      }
      toCreate.add(
          new UserScimGroupMembership(
              UUID.randomUUID(),
              user,
              group.value(),
              group.display() == null ? group.value() : group.display(),
              now));
    }
    membershipRepository.saveAll(toCreate);
  }

  private String displayNameFrom(ScimUserRequest request) {
    if (request.displayName() != null && !request.displayName().isBlank()) {
      return request.displayName();
    }
    if (request.name() != null) {
      String full =
          (Objects.toString(request.name().givenName(), "")
                  + " "
                  + Objects.toString(request.name().familyName(), ""))
              .trim();
      if (!full.isBlank()) {
        return full;
      }
    }
    return extractPrimaryEmail(request);
  }

  private String extractPrimaryEmail(ScimUserRequest request) {
    if (request.emails() != null && !request.emails().isEmpty()) {
      return request.emails().get(0).value();
    }
    if (request.userName() != null && !request.userName().isBlank()) {
      return request.userName();
    }
    throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "Email or userName is required");
  }

  private String mapPlatformRole(String displayName, String externalId) {
    if (displayName == null && externalId == null) {
      return null;
    }
    String d = Optional.ofNullable(displayName).orElse("").toLowerCase();
    String e = Optional.ofNullable(externalId).orElse("").toLowerCase();
    boolean admin =
        properties.adminGroupSet().stream().anyMatch(g -> g.equals(d) || g.equals(e));
    return admin ? "PLATFORM_ADMIN" : null;
  }

  private ScimUserResponse toScimUser(User user) {
    List<UserScimGroupMembership> memberships = membershipRepository.findByUserId(user.getId());
    List<ScimUserResponse.GroupRef> groups =
        memberships.stream()
            .map(m -> new ScimUserResponse.GroupRef(m.getGroupExternalId(), m.getGroupDisplayName()))
            .toList();
    return ScimUserResponse.fromUser(user, groups);
  }

  private Boolean asBoolean(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof String s) {
      return Boolean.parseBoolean(s);
    }
    return null;
  }

  private String asString(Object value) {
    return value == null ? "" : value.toString();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
