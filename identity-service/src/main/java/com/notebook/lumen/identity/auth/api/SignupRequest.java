package com.notebook.lumen.identity.auth.api;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record SignupRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 10) String password,
    @NotBlank @Size(max = 160) String name,
    @Nullable @Size(max = 1024) @URL String avatarUrl) {}
