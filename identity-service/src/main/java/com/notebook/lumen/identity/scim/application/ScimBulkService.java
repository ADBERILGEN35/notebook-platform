package com.notebook.lumen.identity.scim.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.api.ScimBulkRequest;
import com.notebook.lumen.identity.scim.api.ScimBulkResponse;
import com.notebook.lumen.identity.scim.api.ScimErrorResponse;
import com.notebook.lumen.identity.scim.api.ScimGroupRequest;
import com.notebook.lumen.identity.scim.api.ScimPatchRequest;
import com.notebook.lumen.identity.scim.api.ScimUserRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ScimBulkService {
  private static final Pattern USERS_ID = Pattern.compile("^/Users/([0-9a-fA-F\\-]{36})$");
  private static final Pattern GROUPS_ID = Pattern.compile("^/Groups/([0-9a-fA-F\\-]{36})$");

  private final ScimService scimService;
  private final ScimProperties properties;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public ScimBulkService(
      ScimService scimService,
      ScimProperties properties,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.scimService = scimService;
    this.properties = properties;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  public ScimBulkResponse execute(ScimBulkRequest request, HttpServletRequest httpRequest) {
    if (!properties.bulkEnabled()) {
      throw new ScimException(HttpStatus.NOT_IMPLEMENTED, "invalidValue", "SCIM_BULK_DISABLED");
    }
    List<ScimBulkRequest.Operation> ops = request.operations();
    if (ops == null || ops.isEmpty()) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST, "invalidValue", "SCIM_BULK_OPERATION_FAILED: no operations");
    }
    if (ops.size() > properties.bulkMaxOperations()) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST, "invalidValue", "SCIM_BULK_TOO_MANY_OPERATIONS");
    }
    int failCap =
        request.failOnErrors() == null ? properties.bulkFailOnErrorsMax() : request.failOnErrors();
    if (failCap <= 0) {
      failCap = 1;
    }
    failCap = Math.min(failCap, properties.bulkFailOnErrorsMax());

    auditService.record(
        "SCIM_BULK_REQUEST_RECEIVED",
        null,
        "SCIM_BULK",
        null,
        httpRequest,
        Map.of("operationCount", ops.size(), "failOnErrors", failCap));

    Map<String, String> bulkRef = new HashMap<>();
    List<ScimBulkResponse.OperationResult> results = new ArrayList<>();
    int successes = 0;
    int errors = 0;

    for (ScimBulkRequest.Operation op : ops) {
      try {
        results.add(dispatch(op, httpRequest, bulkRef));
        successes++;
        indexBulkResult(op, results.get(results.size() - 1), bulkRef);
      } catch (ScimException e) {
        errors++;
        results.add(toBulkError(op, e));
        auditService.record(
            "SCIM_BULK_OPERATION_FAILED",
            null,
            "SCIM_BULK",
            null,
            httpRequest,
            Map.of(
                "bulkId",
                Objects.toString(op.bulkId(), ""),
                "status",
                String.valueOf(e.getStatus().value())));
        if (errors >= failCap) {
          break;
        }
      } catch (RuntimeException e) {
        errors++;
        var wrapped =
            new ScimException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "invalidValue",
                "SCIM_BULK_OPERATION_FAILED: " + e.getClass().getSimpleName());
        results.add(toBulkError(op, wrapped));
        auditService.record(
            "SCIM_BULK_OPERATION_FAILED",
            null,
            "SCIM_BULK",
            null,
            httpRequest,
            Map.of("bulkId", Objects.toString(op.bulkId(), ""), "status", "500"));
        if (errors >= failCap) {
          break;
        }
      }
    }

    auditService.record(
        "SCIM_BULK_COMPLETED",
        null,
        "SCIM_BULK",
        null,
        httpRequest,
        Map.of("successCount", successes, "errorCount", errors, "failOnErrors", failCap));

    return new ScimBulkResponse(List.of(ScimBulkResponse.SCHEMA), results);
  }

  private void indexBulkResult(
      ScimBulkRequest.Operation op,
      ScimBulkResponse.OperationResult result,
      Map<String, String> bulkRef) {
    if (op.bulkId() == null || op.bulkId().isBlank() || result.location() == null) {
      return;
    }
    String id = extractResourceId(result.location());
    if (id != null) {
      bulkRef.put(op.bulkId(), id);
    }
  }

  private static String extractResourceId(String location) {
    if (location == null) {
      return null;
    }
    int i = location.lastIndexOf('/');
    if (i < 0 || i + 1 >= location.length()) {
      return null;
    }
    return location.substring(i + 1);
  }

  private ScimBulkResponse.OperationResult toBulkError(
      ScimBulkRequest.Operation op, ScimException e) {
    var err = ScimErrorResponse.of(e.getStatus().value(), e.getScimType(), e.getMessage());
    return new ScimBulkResponse.OperationResult(
        op.method(),
        op.bulkId(),
        null,
        String.valueOf(e.getStatus().value()),
        null,
        objectMapper.valueToTree(err));
  }

  private ScimBulkResponse.OperationResult dispatch(
      ScimBulkRequest.Operation op, HttpServletRequest httpRequest, Map<String, String> bulkRef) {
    String method = op.method() == null ? "" : op.method().trim().toUpperCase(Locale.ROOT);
    String path = normalizePath(op.path());
    JsonNode data = op.data();

    if ("POST".equals(method) && "/Users".equals(path)) {
      requireBody(data, path);
      ScimUserRequest u = objectMapper.convertValue(data, ScimUserRequest.class);
      var created = scimService.createUser(u, httpRequest, bulkRef);
      return new ScimBulkResponse.OperationResult(
          method,
          op.bulkId(),
          "/scim/v2/Users/" + created.id(),
          "201",
          objectMapper.valueToTree(created),
          null);
    }
    Matcher umPut = USERS_ID.matcher(path);
    if ("PUT".equals(method) && umPut.matches()) {
      requireBody(data, path);
      UUID id = UUID.fromString(umPut.group(1));
      ScimUserRequest u = objectMapper.convertValue(data, ScimUserRequest.class);
      var body = scimService.putUser(id, u, httpRequest, bulkRef);
      return new ScimBulkResponse.OperationResult(
          method, op.bulkId(), "/scim/v2/Users/" + id, "200", objectMapper.valueToTree(body), null);
    }
    Matcher umPatch = USERS_ID.matcher(path);
    if ("PATCH".equals(method) && umPatch.matches()) {
      requireBody(data, path);
      UUID id = UUID.fromString(umPatch.group(1));
      ScimPatchRequest p = objectMapper.convertValue(data, ScimPatchRequest.class);
      var body = scimService.patchUser(id, p, httpRequest, bulkRef);
      return new ScimBulkResponse.OperationResult(
          method, op.bulkId(), "/scim/v2/Users/" + id, "200", objectMapper.valueToTree(body), null);
    }
    Matcher umDel = USERS_ID.matcher(path);
    if ("DELETE".equals(method) && umDel.matches()) {
      UUID id = UUID.fromString(umDel.group(1));
      scimService.deleteUser(id, httpRequest);
      return new ScimBulkResponse.OperationResult(
          method, op.bulkId(), "/scim/v2/Users/" + id, "204", null, null);
    }

    if ("POST".equals(method) && "/Groups".equals(path)) {
      requireBody(data, path);
      ScimGroupRequest g = objectMapper.convertValue(data, ScimGroupRequest.class);
      var created = scimService.upsertGroup(null, g, httpRequest, bulkRef);
      return new ScimBulkResponse.OperationResult(
          method,
          op.bulkId(),
          "/scim/v2/Groups/" + created.id(),
          "201",
          objectMapper.valueToTree(created),
          null);
    }
    Matcher gmPut = GROUPS_ID.matcher(path);
    if ("PUT".equals(method) && gmPut.matches()) {
      requireBody(data, path);
      UUID id = UUID.fromString(gmPut.group(1));
      ScimGroupRequest g = objectMapper.convertValue(data, ScimGroupRequest.class);
      var body = scimService.upsertGroup(id.toString(), g, httpRequest, bulkRef);
      return new ScimBulkResponse.OperationResult(
          method,
          op.bulkId(),
          "/scim/v2/Groups/" + id,
          "200",
          objectMapper.valueToTree(body),
          null);
    }
    Matcher gmPatch = GROUPS_ID.matcher(path);
    if ("PATCH".equals(method) && gmPatch.matches()) {
      requireBody(data, path);
      UUID id = UUID.fromString(gmPatch.group(1));
      ScimPatchRequest p = objectMapper.convertValue(data, ScimPatchRequest.class);
      var body = scimService.patchGroup(id, p, httpRequest, bulkRef);
      return new ScimBulkResponse.OperationResult(
          method,
          op.bulkId(),
          "/scim/v2/Groups/" + id,
          "200",
          objectMapper.valueToTree(body),
          null);
    }
    Matcher gmDel = GROUPS_ID.matcher(path);
    if ("DELETE".equals(method) && gmDel.matches()) {
      UUID id = UUID.fromString(gmDel.group(1));
      scimService.deleteGroup(id, httpRequest);
      return new ScimBulkResponse.OperationResult(
          method, op.bulkId(), "/scim/v2/Groups/" + id, "204", null, null);
    }

    throw new ScimException(
        HttpStatus.BAD_REQUEST,
        "invalidValue",
        "SCIM_BULK_OPERATION_UNSUPPORTED: " + method + " " + path);
  }

  private static void requireBody(JsonNode data, String path) {
    if (data == null || data.isNull()) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST,
          "invalidValue",
          "SCIM_BULK_OPERATION_FAILED: missing data for " + path);
    }
  }

  private static String normalizePath(String path) {
    if (path == null) {
      return "";
    }
    String p = path.trim();
    if (!p.startsWith("/")) {
      p = "/" + p;
    }
    int q = p.indexOf('?');
    if (q >= 0) {
      p = p.substring(0, q);
    }
    return p;
  }
}
