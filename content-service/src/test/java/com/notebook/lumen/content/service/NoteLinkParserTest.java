package com.notebook.lumen.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class NoteLinkParserTest {
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final NoteLinkParser parser = new NoteLinkParser();

  @Test
  void parsesPropsAndHrefAndIgnoresSelfLinks() throws Exception {
    UUID self = UUID.randomUUID();
    UUID target1 = UUID.randomUUID();
    UUID target2 = UUID.randomUUID();
    var json =
        objectMapper.readTree(
            """
            [{"id":"1","type":"paragraph","props":{"noteId":"%s","href":"note://%s"},"content":[{"href":"note://%s"}]}]
            """
                .formatted(target1, target2, self));

    assertThat(parser.parse(json, self)).containsExactly(target1, target2);
  }
}
