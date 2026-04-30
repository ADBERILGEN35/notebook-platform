package com.notebook.lumen.identity.user.mapper;

import com.notebook.lumen.identity.user.api.UserResponse;
import com.notebook.lumen.identity.user.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

  UserResponse toResponse(User user);
}
