package com.notebook.lumen.identity.user.infrastructure;

import com.notebook.lumen.identity.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByScimExternalId(String scimExternalId);

  Page<User> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
