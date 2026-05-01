package com.notebook.lumen.content.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.shared.exception.ContentException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class BlockValidationServiceTest {
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final BlockValidationService service =
      new BlockValidationService(
          new ContentProperties(
              new ContentProperties.Blocks(false, 8, 262144),
              new ContentProperties.Workspace(
                  "http://localhost", 1000, 50, 10000, 2, "", "", "", "dual"),
              null));

  @Test
  void validatesRecursiveChildren() throws Exception {
    service.validate(
        objectMapper.readTree(
            """
            [{"id":"1","type":"paragraph","children":[{"id":"2","type":"quote"}]}]
            """));
  }

  @Test
  void rejectsUnknownType() throws Exception {
    var json = objectMapper.readTree("[{\"id\":\"1\",\"type\":\"unknown\"}]");
    assertThatThrownBy(() -> service.validate(json))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("Unknown block type");
  }

  @Test
  void rejectsNonArrayRoot() throws Exception {
    var json = objectMapper.readTree("{\"id\":\"1\",\"type\":\"paragraph\"}");
    assertThatThrownBy(() -> service.validate(json)).isInstanceOf(ContentException.class);
  }

  @Test
  void rejectsTooDeepNesting() throws Exception {
    BlockValidationService strict =
        new BlockValidationService(
            new ContentProperties(
                new ContentProperties.Blocks(false, 1, 262144),
                new ContentProperties.Workspace(
                    "http://localhost", 1000, 50, 10000, 2, "", "", "", "dual"),
                null));
    var json =
        objectMapper.readTree(
            """
            [{"id":"1","type":"paragraph","children":[{"id":"2","type":"paragraph"}]}]
            """);

    assertThatThrownBy(() -> strict.validate(json))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("nesting depth");
  }
}
