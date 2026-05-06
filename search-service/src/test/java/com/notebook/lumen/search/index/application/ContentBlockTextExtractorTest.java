package com.notebook.lumen.search.index.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.search.shared.config.SearchProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ContentBlockTextExtractorTest {
  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void extractsRecursiveBlockText() throws Exception {
    ContentBlockTextExtractor extractor = new ContentBlockTextExtractor(properties(200000));

    String text =
        extractor.extract(
            objectMapper.readTree(
                """
                [
                  {
                    "type":"heading_1",
                    "content":[{"type":"text","text":"Roadmap"}],
                    "children":[{"type":"paragraph","content":[{"text":"Nested detail"}]}]
                  },
                  {"type":"table","cells":[[{"text":"Cell value"}]]}
                ]
                """));

    assertThat(text).contains("Roadmap", "Nested detail", "Cell value");
  }

  @Test
  void capsIndexedTextLength() throws Exception {
    ContentBlockTextExtractor extractor = new ContentBlockTextExtractor(properties(8));

    String text = extractor.extract(objectMapper.readTree("[{\"text\":\"abcdefghijklmnop\"}]"));

    assertThat(text).hasSize(8);
  }

  private SearchProperties properties(int maxChars) {
    return new SearchProperties(
        maxChars,
        120,
        2,
        50,
        new SearchProperties.Workspace("http://localhost", 1000, 2),
        null,
        new SearchProperties.Internal(null));
  }
}
