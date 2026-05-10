package com.notebook.lumen.content.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/status")
public class InternalContentAdminStatusController {
  private final InternalAdminStatusProperties statusProperties;
  private final ContentAdminStatusAuthorizer authorizer;
  private final ContentAdminStatusService statusService;

  public InternalContentAdminStatusController(
      InternalAdminStatusProperties statusProperties,
      ContentAdminStatusAuthorizer authorizer,
      ContentAdminStatusService statusService) {
    this.statusProperties = statusProperties;
    this.authorizer = authorizer;
    this.statusService = statusService;
  }

  @GetMapping(path = "/content", produces = MediaType.APPLICATION_JSON_VALUE)
  public ContentAdminStatusService.ContentAdminStatusResponse content(
      @RequestHeader(value = ContentAdminStatusAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization) {
    if (!statusProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Internal admin status is disabled");
    }
    authorizer.authorize(serviceAuthorization);
    return statusService.build();
  }
}
