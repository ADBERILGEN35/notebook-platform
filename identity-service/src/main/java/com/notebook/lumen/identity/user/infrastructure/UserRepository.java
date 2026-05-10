package com.notebook.lumen.identity.user.infrastructure;

import com.notebook.lumen.identity.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByScimExternalId(String scimExternalId);

  Page<User> findAllByOrderByCreatedAtAsc(Pageable pageable);

  @Query(
      "SELECT u FROM User u WHERE u.deletedAt IS NULL AND "
          + "(:q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT(:q, '%')) OR LOWER(u.name) LIKE LOWER(CONCAT(:q, '%')))")
  Page<User> pageForAdminRbacDirectory(@Param("q") String q, Pageable pageable);
}
