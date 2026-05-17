package com.notebook.lumen.identity.scim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.api.ScimBulkRequest;
import com.notebook.lumen.identity.scim.api.ScimUserRequest;
import com.notebook.lumen.identity.scim.api.ScimUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ScimBulkServiceTest {

  @Mock private ScimService scimService;
  @Mock private AuditService auditService;
  @Mock private HttpServletRequest httpRequest;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  private ScimBulkService newService(ScimProperties props) {
    return new ScimBulkService(scimService, props, auditService, objectMapper);
  }

  @Test
  void bulkDisabledThrows() {
    var props =
        ScimProperties.withLegacyDefaults(true, "t", "", true, "g", true, 5, false, 100, 10);
    var svc = newService(props);
    JsonNode data =
        objectMapper.valueToTree(
            new ScimUserRequest("a@b.com", null, "n", List.of(), true, null, List.of()));
    var req =
        new ScimBulkRequest(
            List.of("urn:ietf:params:scim:api:messages:2.0:BulkRequest"),
            10,
            List.of(new ScimBulkRequest.Operation("POST", "/Users", "b1", data)));
    assertThatThrownBy(() -> svc.execute(req, httpRequest))
        .isInstanceOf(ScimException.class)
        .satisfies(
            ex -> {
              assertThat(((ScimException) ex).getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
              assertThat(ex.getMessage()).contains("SCIM_BULK_DISABLED");
            });
    verify(scimService, never()).createUser(any(), any(), any());
  }

  @Test
  void tooManyOperationsThrows() {
    var props = ScimProperties.withLegacyDefaults(true, "t", "", true, "g", true, 5, true, 2, 10);
    var svc = newService(props);
    JsonNode data = objectMapper.createObjectNode();
    var ops =
        List.of(
            new ScimBulkRequest.Operation("POST", "/Users", "1", data),
            new ScimBulkRequest.Operation("POST", "/Users", "2", data),
            new ScimBulkRequest.Operation("POST", "/Users", "3", data));
    var req =
        new ScimBulkRequest(List.of("urn:ietf:params:scim:api:messages:2.0:BulkRequest"), 10, ops);
    assertThatThrownBy(() -> svc.execute(req, httpRequest))
        .isInstanceOf(ScimException.class)
        .satisfies(ex -> assertThat(ex.getMessage()).contains("SCIM_BULK_TOO_MANY_OPERATIONS"));
  }

  @Test
  void failOnErrorsStopsAfterThreshold() {
    var props = ScimProperties.withLegacyDefaults(true, "t", "", true, "g", true, 5, true, 100, 10);
    var svc = newService(props);
    when(scimService.createUser(any(), any(), any()))
        .thenThrow(new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", "bad"));
    JsonNode data =
        objectMapper.valueToTree(
            new ScimUserRequest("a@b.com", null, "n", List.of(), true, null, List.of()));
    var ops =
        List.of(
            new ScimBulkRequest.Operation("POST", "/Users", "1", data),
            new ScimBulkRequest.Operation("POST", "/Users", "2", data));
    var req =
        new ScimBulkRequest(List.of("urn:ietf:params:scim:api:messages:2.0:BulkRequest"), 1, ops);
    var resp = svc.execute(req, httpRequest);
    assertThat(resp.operations()).hasSize(1);
    verify(scimService).createUser(any(), any(), any());
  }

  @Test
  void postUserSuccessRecordsBulkIdInContextForLaterOps() {
    var props = ScimProperties.withLegacyDefaults(true, "t", "", true, "g", true, 5, true, 100, 10);
    var svc = newService(props);
    UUID uid = UUID.randomUUID();
    when(scimService.createUser(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ScimUserRequest ur = inv.getArgument(0);
              return new ScimUserResponse(
                  List.of(ScimUserResponse.CORE_SCHEMA),
                  uid.toString(),
                  ur.userName(),
                  new ScimUserResponse.Name("", ""),
                  ur.displayName(),
                  List.of(new ScimUserResponse.Email(ur.userName(), "work", true)),
                  true,
                  ur.externalId(),
                  List.of(),
                  new ScimUserResponse.Meta("User", null, null));
            });
    JsonNode data =
        objectMapper.valueToTree(
            new ScimUserRequest("a@b.com", null, "n", List.of(), true, null, List.of()));
    var req =
        new ScimBulkRequest(
            List.of("urn:ietf:params:scim:api:messages:2.0:BulkRequest"),
            10,
            List.of(new ScimBulkRequest.Operation("POST", "/Users", "u1", data)));
    var resp = svc.execute(req, httpRequest);
    assertThat(resp.operations().get(0).status()).isEqualTo("201");
    assertThat(resp.operations().get(0).location()).endsWith(uid.toString());
  }
}
