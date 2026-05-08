package com.notebook.lumen.identity.sso.application;

public record OidcProviderMetadata(
    String authorizationEndpoint, String tokenEndpoint, String jwksUri, String issuer) {}
