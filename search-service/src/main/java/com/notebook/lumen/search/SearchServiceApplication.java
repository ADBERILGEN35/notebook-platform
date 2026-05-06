package com.notebook.lumen.search;

import com.notebook.lumen.search.shared.config.SearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(SearchProperties.class)
@EnableScheduling
public class SearchServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(SearchServiceApplication.class, args);
  }
}
