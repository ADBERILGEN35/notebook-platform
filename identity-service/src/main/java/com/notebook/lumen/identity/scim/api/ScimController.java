package com.notebook.lumen.identity.scim.api;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.application.ScimAuthService;
import com.notebook.lumen.identity.scim.application.ScimBulkService;
import com.notebook.lumen.identity.scim.application.ScimService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scim/v2")
public class ScimController {
  private final ScimAuthService scimAuthService;
  private final ScimService scimService;
  private final ScimBulkService scimBulkService;
  private final ScimProperties scimProperties;

  public ScimController(
      ScimAuthService scimAuthService,
      ScimService scimService,
      ScimBulkService scimBulkService,
      ScimProperties scimProperties) {
    this.scimAuthService = scimAuthService;
    this.scimService = scimService;
    this.scimBulkService = scimBulkService;
    this.scimProperties = scimProperties;
  }

  @GetMapping("/ServiceProviderConfig")
  public Map<String, Object> serviceProviderConfig(HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return Map.of(
        "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
        "patch", Map.of("supported", true),
        "bulk",
            Map.of("supported", scimProperties.bulkEnabled(), "maxOperations", scimProperties.bulkMaxOperations()),
        "filter", Map.of("supported", true, "maxResults", 200),
        "changePassword", Map.of("supported", false),
        "sort", Map.of("supported", false),
        "etag", Map.of("supported", false),
        "authenticationSchemes",
            List.of(
                Map.of(
                    "type", "oauthbearertoken",
                    "name", "OAuth Bearer Token",
                    "description", "Static bearer token for SCIM provisioning")));
  }

  @GetMapping("/Schemas")
  public Map<String, Object> schemas(HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return Map.of(
        "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
        "totalResults", 2,
        "startIndex", 1,
        "itemsPerPage", 2,
        "Resources",
            List.of(
                Map.of("id", ScimUserResponse.CORE_SCHEMA, "name", "User"),
                Map.of("id", ScimGroupResponse.CORE_SCHEMA, "name", "Group")));
  }

  @GetMapping("/ResourceTypes")
  public Map<String, Object> resourceTypes(HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return Map.of(
        "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
        "totalResults", 2,
        "startIndex", 1,
        "itemsPerPage", 2,
        "Resources",
            List.of(
                Map.of("id", "User", "name", "User", "endpoint", "/Users", "schema", ScimUserResponse.CORE_SCHEMA),
                Map.of(
                    "id",
                    "Group",
                    "name",
                    "Group",
                    "endpoint",
                    "/Groups",
                    "schema",
                    ScimGroupResponse.CORE_SCHEMA)));
  }

  @GetMapping("/Users")
  public ScimListResponse<ScimUserResponse> listUsers(
      @RequestParam(defaultValue = "1") int startIndex,
      @RequestParam(defaultValue = "50") int count,
      @RequestParam(required = false) String filter,
      HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.listUsers(startIndex, count, filter);
  }

  @PostMapping("/Users")
  @ResponseStatus(HttpStatus.CREATED)
  public ScimUserResponse createUser(
      @RequestBody ScimUserRequest payload, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.createUser(payload, request);
  }

  @GetMapping("/Users/{id}")
  public ScimUserResponse getUser(@PathVariable UUID id, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.getUser(id);
  }

  @PutMapping("/Users/{id}")
  public ScimUserResponse putUser(
      @PathVariable UUID id, @RequestBody ScimUserRequest payload, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.putUser(id, payload, request);
  }

  @PatchMapping("/Users/{id}")
  public ScimUserResponse patchUser(
      @PathVariable UUID id, @RequestBody ScimPatchRequest payload, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.patchUser(id, payload, request);
  }

  @DeleteMapping("/Users/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable UUID id, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    scimService.deleteUser(id, request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/Groups")
  public ScimListResponse<ScimGroupResponse> listGroups(
      @RequestParam(defaultValue = "1") int startIndex,
      @RequestParam(defaultValue = "50") int count,
      HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.listGroups(startIndex, count);
  }

  @PostMapping("/Groups")
  @ResponseStatus(HttpStatus.CREATED)
  public ScimGroupResponse createGroup(
      @RequestBody ScimGroupRequest payload, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.upsertGroup(null, payload, request);
  }

  @GetMapping("/Groups/{id}")
  public ScimGroupResponse getGroup(@PathVariable UUID id, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.getGroup(id);
  }

  @PutMapping("/Groups/{id}")
  public ScimGroupResponse putGroup(
      @PathVariable String id, @RequestBody ScimGroupRequest payload, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.upsertGroup(id, payload, request);
  }

  @PatchMapping("/Groups/{id}")
  public ScimGroupResponse patchGroup(
      @PathVariable UUID id, @RequestBody ScimPatchRequest payload, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimService.patchGroup(id, payload, request);
  }

  @DeleteMapping("/Groups/{id}")
  public ResponseEntity<Void> deleteGroup(@PathVariable UUID id, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    scimService.deleteGroup(id, request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/Bulk")
  public ScimBulkResponse bulk(@RequestBody ScimBulkRequest payload, HttpServletRequest request) {
    scimAuthService.requireAuthorized(request);
    return scimBulkService.execute(payload, request);
  }
}
