package com.englishos.identity.infrastructure;

import java.util.Optional;

import com.englishos.identity.domain.Role;
import com.englishos.identity.domain.RoleCode;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(RoleCode code);
}
