package com.notebook.lumen.identity.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.argon2")
public class Argon2Properties {

  private int saltLength;
  private int hashLength;
  private int parallelism;
  private int memoryInKb;
  private int iterations;

  public int getSaltLength() {
    return saltLength;
  }

  public void setSaltLength(int saltLength) {
    this.saltLength = saltLength;
  }

  public int getHashLength() {
    return hashLength;
  }

  public void setHashLength(int hashLength) {
    this.hashLength = hashLength;
  }

  public int getParallelism() {
    return parallelism;
  }

  public void setParallelism(int parallelism) {
    this.parallelism = parallelism;
  }

  public int getMemoryInKb() {
    return memoryInKb;
  }

  public void setMemoryInKb(int memoryInKb) {
    this.memoryInKb = memoryInKb;
  }

  public int getIterations() {
    return iterations;
  }

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }
}
