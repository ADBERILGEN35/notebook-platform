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
import com.notebook.lumen.identity.scim.domain.ScimGroupMembership;
import com.notebook.lumen.identity.scim.domain.ScimMemberType;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupMembershipRepository;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
  private final ScimGroupMembershipRepository membershipRepository;
  private final ScimGroupRepository scimGroupRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final ScimProperties properties;
  private final ScimGroupGraphValidation graphValidation;

  public ScimService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      ScimGroupMembershipRepository membershipRepository,
      ScimGroupRepository scimGroupRepository,
      PasswordEncoder passwordEncoder,
      AuditService auditService,
      ScimProperties properties,
      ScimGroupGraphValidation graphValidation) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.membershipRepository = membershipRepository;
    this.scimGroupRepository = scimGroupRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.properties = properties;
    this.graphValidation = graphValidation;
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

  public ScimUserResponse createUser(ScimUserRequest request, HttpServletRequest httpRequest) {
    return createUser(request, httpRequest, Map.of());
  }

  @Transactional
  public ScimUserResponse createUser(
      ScimUserRequest request, HttpServletRequest httpRequest, Map<String, String> bulkIdToResourceId) {
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
    replaceUserMemberships(created, request.groups(), httpRequest, bulkIdToResourceId);
    auditService.record(
        "SCIM_USER_CREATED",
        null,
        "USER",
        created.getId(),
        httpRequest,
        Map.of("email", created.getEmail(), "externalId", Objects.toString(created.getScimExternalId(), "")));
    return toScimUser(created);
  }

  public ScimUserResponse putUser(UUID id, ScimUserRequest request, HttpServletRequest httpRequest) {
    return putUser(id, request, httpRequest, Map.of());
  }

  @Transactional
  public ScimUserResponse putUser(
      UUID id, ScimUserRequest request, HttpServletRequest httpRequest, Map<String, String> bulkIdToResourceId) {
    User user = requireUser(id);
    updateUserFields(user, request);
    userRepository.save(user);
    replaceUserMemberships(user, request.groups(), httpRequest, bulkIdToResourceId);
    auditService.record(
        "SCIM_USER_UPDATED",
        null,
        "USER",
        user.getId(),
        httpRequest,
        Map.of("email", user.getEmail()));
    return toScimUser(user);
  }

  public ScimUserResponse patchUser(UUID id, ScimPatchRequest request, HttpServletRequest httpRequest) {
    return patchUser(id, request, httpRequest, Map.of());
  }

  @Transactional
  public ScimUserResponse patchUser(
      UUID id, ScimPatchRequest request, HttpServletRequest httpRequest, Map<String, String> bulkIdToResourceId) {
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
        replaceUserMemberships(
            user, ScimPatchRequest.readGroups(op.value().get("groups")), httpRequest, bulkIdToResourceId);
        auditService.record(
            "SCIM_USER_GROUPS_UPDATED",
            null,
            "USER",
            user.getId(),
            httpRequest,
            Map.of(
                "groupsCount",
                membershipRepository.findByMemberTypeAndMemberUser_Id(ScimMemberType.USER, user.getId()).size()));
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
        scimGroupRepository
            .findAllByActiveIsTrue(PageRequest.of(page, Math.max(1, count)))
            .stream()
            .map(
                g ->
                    ScimGroupResponse.summary(
                        g.getId().toString(),
                        g.getExternalId(),
                        g.getDisplayName(),
                        g.isActive()))
            .toList();
    return ScimListResponse.of(groups.size(), startIndex, count, groups);
  }

  @Transactional(readOnly = true)
  public ScimGroupResponse getGroup(UUID id) {
    if (!properties.groupsEnabled()) {
      throw new ScimException(HttpStatus.NOT_IMPLEMENTED, "invalidTarget", "SCIM groups disabled");
    }
    ScimGroup group =
        scimGroupRepository
            .findByIdAndActiveIsTrue(id)
            .orElseThrow(
                () -> new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_NOT_FOUND"));
    return toGroupResponse(group);
  }

  @Transactional
  public ScimGroupResponse upsertGroup(
      String idOrNull,
      ScimGroupRequest req,
      HttpServletRequest request,
      Map<String, String> bulkIdToResourceId) {
    if (!properties.groupsEnabled()) {
      throw new ScimException(HttpStatus.NOT_IMPLEMENTED, "invalidTarget", "SCIM groups disabled");
    }
    if (req.displayName() == null || req.displayName().isBlank()) {
      throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "displayName is required");
    }
    boolean created;
    ScimGroup group;
    Instant now = Instant.now();
    if (idOrNull == null) {
      Optional<ScimGroup> existing =
          req.externalId() != null && !req.externalId().isBlank()
              ? scimGroupRepository.findByExternalId(req.externalId())
              : Optional.empty();
      if (existing.isPresent()) {
        group = existing.get();
        if (!group.isActive()) {
          throw new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_NOT_FOUND");
        }
        created = false;
      } else {
        group =
            new ScimGroup(
                UUID.randomUUID(),
                blankToNull(req.externalId()),
                req.displayName(),
                mapPlatformRole(req.displayName(), req.externalId()),
                null,
                true,
                now,
                now);
        created = true;
      }
    } else {
      UUID gid = UUID.fromString(idOrNull);
      group =
          scimGroupRepository
              .findById(gid)
              .filter(ScimGroup::isActive)
              .orElseThrow(
                  () -> new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_NOT_FOUND"));
      created = false;
    }
    group.update(
        req.displayName(),
        mapPlatformRole(req.displayName(), req.externalId()),
        blankToNull(req.externalId()),
        group.getProvider());
    scimGroupRepository.save(group);
    auditService.record(
        created ? "SCIM_GROUP_CREATED" : "SCIM_GROUP_UPDATED",
        null,
        "SCIM_GROUP",
        group.getId(),
        request,
        Map.of("externalId", Objects.toString(group.getExternalId(), ""), "displayName", group.getDisplayName()));
    if (req.members() != null) {
      replaceGroupMemberships(group, req.members(), request, bulkIdToResourceId);
    }
    return toGroupResponse(group);
  }

  public ScimGroupResponse upsertGroup(String idOrNull, ScimGroupRequest req, HttpServletRequest request) {
    return upsertGroup(idOrNull, req, request, Map.of());
  }

  public ScimGroupResponse patchGroup(UUID id, ScimPatchRequest request, HttpServletRequest httpRequest) {
    return patchGroup(id, request, httpRequest, Map.of());
  }

  @Transactional
  public ScimGroupResponse patchGroup(
      UUID id, ScimPatchRequest request, HttpServletRequest httpRequest, Map<String, String> bulkIdToResourceId) {
    if (!properties.groupsEnabled()) {
      throw new ScimException(HttpStatus.NOT_IMPLEMENTED, "invalidTarget", "SCIM groups disabled");
    }
    ScimGroup group =
        scimGroupRepository
            .findById(id)
            .filter(ScimGroup::isActive)
            .orElseThrow(
                () -> new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_NOT_FOUND"));
    for (ScimPatchRequest.Operation op : request.operations()) {
      if (!"replace".equalsIgnoreCase(op.op())) {
        throw new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", "Only replace is supported");
      }
      String path = op.path() == null ? "" : op.path();
      if ("displayName".equalsIgnoreCase(path)) {
        String dn = asString(op.value().get("displayName"));
        if (dn.isBlank()) {
          throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "displayName is required");
        }
        group.update(
            dn,
            mapPlatformRole(dn, group.getExternalId()),
            group.getExternalId(),
            group.getProvider());
      } else if ("externalId".equalsIgnoreCase(path)) {
        String ext = blankToNull(asString(op.value().get("externalId")));
        group.update(
            group.getDisplayName(),
            mapPlatformRole(group.getDisplayName(), ext),
            ext,
            group.getProvider());
      } else if ("members".equalsIgnoreCase(path)) {
        replaceGroupMemberships(
            group, ScimPatchRequest.readGroupMembers(op.value().get("members")), httpRequest, bulkIdToResourceId);
      } else {
        throw new ScimException(HttpStatus.BAD_REQUEST, "invalidPath", "Unsupported patch path: " + path);
      }
    }
    scimGroupRepository.save(group);
    auditService.record(
        "SCIM_GROUP_UPDATED",
        null,
        "SCIM_GROUP",
        group.getId(),
        httpRequest,
        Map.of("externalId", Objects.toString(group.getExternalId(), "")));
    return toGroupResponse(group);
  }

  @Transactional
  public void deleteGroup(UUID id, HttpServletRequest request) {
    ScimGroup group =
        scimGroupRepository
            .findById(id)
            .orElseThrow(
                () -> new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_NOT_FOUND"));
    membershipRepository.deleteByGroup_Id(id);
    membershipRepository.deleteByMemberGroup_Id(id);
    group.deactivate();
    scimGroupRepository.save(group);
    auditService.record(
        "SCIM_GROUP_DEPROVISIONED",
        null,
        "SCIM_GROUP",
        group.getId(),
        request,
        Map.of("externalId", Objects.toString(group.getExternalId(), "")));
  }

  private ScimGroupResponse toGroupResponse(ScimGroup group) {
    List<ScimGroupResponse.Member> members = buildMemberViews(group.getId());
    return ScimGroupResponse.withMembers(
        group.getId().toString(),
        group.getExternalId(),
        group.getDisplayName(),
        group.isActive(),
        members,
        new ScimGroupResponse.Meta("Group", group.getCreatedAt(), group.getUpdatedAt()));
  }

  private List<ScimGroupResponse.Member> buildMemberViews(UUID groupId) {
    List<ScimGroupResponse.Member> out = new ArrayList<>();
    for (ScimGroupMembership m : membershipRepository.findByGroup_Id(groupId)) {
      if (m.getMemberType() == ScimMemberType.USER && m.getMemberUser() != null) {
        User u = m.getMemberUser();
        out.add(
            new ScimGroupResponse.Member(
                u.getId().toString(),
                "/scim/v2/Users/" + u.getId(),
                u.getEmail(),
                "User"));
      } else if (m.getMemberType() == ScimMemberType.GROUP && m.getMemberGroup() != null) {
        ScimGroup g = m.getMemberGroup();
        out.add(
            new ScimGroupResponse.Member(
                g.getId().toString(),
                "/scim/v2/Groups/" + g.getId(),
                g.getDisplayName(),
                "Group"));
      }
    }
    return out;
  }

  private void replaceGroupMemberships(
      ScimGroup group,
      List<ScimGroupRequest.Member> members,
      HttpServletRequest request,
      Map<String, String> bulkIdToResourceId) {
    if (members == null) {
      return;
    }
    List<ScimGroupMembership> toSave = new ArrayList<>();
    Set<String> dedupe = new HashSet<>();
    Instant now = Instant.now();
    for (ScimGroupRequest.Member raw : members) {
      if (raw == null || raw.value() == null || raw.value().isBlank()) {
        continue;
      }
      String t = raw.type() == null ? "" : raw.type().trim();
      if ("group".equalsIgnoreCase(t)) {
        appendGroupMemberEdge(group, raw, bulkIdToResourceId, toSave, dedupe, now);
      } else if ("user".equalsIgnoreCase(t)) {
        appendUserMemberEdge(group, raw, bulkIdToResourceId, toSave, dedupe, now);
      } else if (t.isEmpty()) {
        String v = resolveBulkValue(raw.value(), bulkIdToResourceId);
        UUID maybeId = tryParseUuid(v);
        if (maybeId != null) {
          if (userRepository.findById(maybeId).isPresent()) {
            appendUserMemberEdge(group, raw, bulkIdToResourceId, toSave, dedupe, now);
          } else if (properties.groupNestingEnabled()) {
            appendGroupMemberEdge(group, raw, bulkIdToResourceId, toSave, dedupe, now);
          } else {
            throw new ScimException(
                HttpStatus.NOT_FOUND,
                "notFound",
                "SCIM_GROUP_MEMBER_NOT_FOUND: user not found");
          }
        } else if (properties.groupNestingEnabled()) {
          appendGroupMemberEdge(group, raw, bulkIdToResourceId, toSave, dedupe, now);
        } else {
          throw new ScimException(
              HttpStatus.BAD_REQUEST,
              "invalidValue",
              "SCIM_INVALID_GROUP_MEMBER: specify type User or Group for non-UUID values");
        }
      } else {
        throw new ScimException(
            HttpStatus.BAD_REQUEST, "invalidValue", "SCIM_INVALID_GROUP_MEMBER: unsupported member type");
      }
    }
    membershipRepository.deleteByGroup_Id(group.getId());
    membershipRepository.saveAll(toSave);
    auditService.record(
        "SCIM_GROUP_MEMBERSHIP_UPDATED",
        null,
        "SCIM_GROUP",
        group.getId(),
        request,
        Map.of("memberCount", toSave.size()));
  }

  private void appendUserMemberEdge(
      ScimGroup group,
      ScimGroupRequest.Member raw,
      Map<String, String> bulkIdToResourceId,
      List<ScimGroupMembership> toSave,
      Set<String> dedupe,
      Instant now) {
    User user = resolveUserRef(raw.value(), bulkIdToResourceId);
    String key = "U:" + user.getId();
    if (!dedupe.add(key)) {
      return;
    }
    toSave.add(
        new ScimGroupMembership(
            UUID.randomUUID(),
            group,
            ScimMemberType.USER,
            user,
            null,
            user.getScimExternalId(),
            now));
  }

  private void appendGroupMemberEdge(
      ScimGroup group,
      ScimGroupRequest.Member raw,
      Map<String, String> bulkIdToResourceId,
      List<ScimGroupMembership> toSave,
      Set<String> dedupe,
      Instant now) {
    if (!properties.groupNestingEnabled()) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST,
          "invalidValue",
          "SCIM_INVALID_GROUP_MEMBER: nested group membership is disabled");
    }
    ScimGroup child = resolveGroupRef(raw.value(), raw.display(), bulkIdToResourceId);
    if (!child.isActive()) {
      throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "SCIM_GROUP_NOT_FOUND");
    }
    String key = "G:" + child.getId();
    if (!dedupe.add(key)) {
      return;
    }
    graphValidation.validateNewNestedMembership(
        group.getId(), child.getId(), properties.groupNestingMaxDepth());
    toSave.add(
        new ScimGroupMembership(
            UUID.randomUUID(),
            group,
            ScimMemberType.GROUP,
            null,
            child,
            child.getExternalId(),
            now));
  }

  private User resolveUserRef(String value, Map<String, String> bulkIdToResourceId) {
    String v = resolveBulkValue(value, bulkIdToResourceId);
    UUID uid = tryParseUuid(v);
    if (uid == null) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST, "invalidValue", "SCIM_GROUP_MEMBER_NOT_FOUND: user value must be a UUID");
    }
    return userRepository
        .findById(uid)
        .orElseThrow(
            () ->
                new ScimException(
                    HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_MEMBER_NOT_FOUND: user not found"));
  }

  private ScimGroup resolveGroupRef(String value, String display, Map<String, String> bulkIdToResourceId) {
    String v = resolveBulkValue(value, bulkIdToResourceId);
    UUID gid = tryParseUuid(v);
    if (gid != null) {
      return scimGroupRepository
          .findById(gid)
          .orElseThrow(
              () ->
                  new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_MEMBER_NOT_FOUND"));
    }
    return scimGroupRepository
        .findByExternalId(v)
        .orElseThrow(
            () ->
                new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_MEMBER_NOT_FOUND"));
  }

  private static String resolveBulkValue(String value, Map<String, String> bulkIdToResourceId) {
    if (value != null && value.startsWith("bulkId:")) {
      String bid = value.substring("bulkId:".length());
      String resolved = bulkIdToResourceId.get(bid);
      if (resolved == null || resolved.isBlank()) {
        throw new ScimException(
            HttpStatus.BAD_REQUEST,
            "invalidValue",
            "SCIM_INVALID_GROUP_MEMBER: unresolved bulkId reference");
      }
      return resolved;
    }
    return value;
  }

  private void replaceUserMemberships(
      User user,
      List<ScimUserRequest.GroupRef> groups,
      HttpServletRequest request,
      Map<String, String> bulkIdToResourceId) {
    membershipRepository.deleteByMemberTypeAndMemberUser_Id(ScimMemberType.USER, user.getId());
    if (groups == null || groups.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    List<ScimGroupMembership> rows = new ArrayList<>();
    for (ScimUserRequest.GroupRef group : groups) {
      if (group.value() == null || group.value().isBlank()) {
        continue;
      }
      ScimGroup g = resolveOrCreateGroupForUserRef(group.value(), group.display(), bulkIdToResourceId);
      if (!g.isActive()) {
        throw new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "SCIM_GROUP_NOT_FOUND");
      }
      rows.add(
          new ScimGroupMembership(
              UUID.randomUUID(),
              g,
              ScimMemberType.USER,
              user,
              null,
              user.getScimExternalId(),
              now));
    }
    membershipRepository.saveAll(rows);
  }

  private ScimGroup resolveOrCreateGroupForUserRef(
      String value, String display, Map<String, String> bulkIdToResourceId) {
    String v = resolveBulkValue(value, bulkIdToResourceId);
    UUID gid = tryParseUuid(v);
    if (gid != null) {
      return scimGroupRepository
          .findById(gid)
          .orElseThrow(
              () -> new ScimException(HttpStatus.NOT_FOUND, "notFound", "SCIM_GROUP_NOT_FOUND"));
    }
    Optional<ScimGroup> byExt = scimGroupRepository.findByExternalId(v);
    if (byExt.isPresent()) {
      return byExt.get();
    }
    Instant now = Instant.now();
    ScimGroup created =
        new ScimGroup(
            UUID.randomUUID(),
            v,
            display == null || display.isBlank() ? v : display,
            mapPlatformRole(display, v),
            null,
            true,
            now,
            now);
    return scimGroupRepository.save(created);
  }

  private static UUID tryParseUuid(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(s.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
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
    String d = Optional.ofNullable(displayName).orElse("").toLowerCase(Locale.ROOT);
    String e = Optional.ofNullable(externalId).orElse("").toLowerCase(Locale.ROOT);
    boolean admin =
        properties.adminGroupSet().stream().anyMatch(g -> g.equals(d) || g.equals(e));
    return admin ? "PLATFORM_ADMIN" : null;
  }

  private ScimUserResponse toScimUser(User user) {
    List<ScimUserResponse.GroupRef> groups =
        membershipRepository.findByMemberTypeAndMemberUser_Id(ScimMemberType.USER, user.getId()).stream()
            .map(ScimGroupMembership::getGroup)
            .filter(ScimGroup::isActive)
            .map(
                g ->
                    new ScimUserResponse.GroupRef(
                        g.getId().toString(),
                        g.getDisplayName() == null || g.getDisplayName().isBlank()
                            ? Objects.toString(g.getExternalId(), g.getId().toString())
                            : g.getDisplayName()))
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
