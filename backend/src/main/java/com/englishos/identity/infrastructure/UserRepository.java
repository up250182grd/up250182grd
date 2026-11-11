package com.englishos.identity.infrastructure;

import java.util.Optional;

import com.englishos.identity.domain.RoleCode;
import com.englishos.identity.domain.User;
import com.englishos.identity.domain.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(String publicId);

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from User u join u.roles r where r.code = :role and u.status <> 'DELETED'")
    boolean existsActiveWithRole(@Param("role") RoleCode role);

    @Query("""
            select count(u) > 0 from User u join u.roles r
            where r.code = com.englishos.identity.domain.RoleCode.ADMIN and u.status = 'ACTIVE' and u.id <> :id""")
    boolean existsOtherActiveAdmin(@Param("id") Long id);

    @Query("""
            select u from User u
            where (:status is null or u.status = :status)
              and (:q is null or u.email like concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, @Param("status") UserStatus status, Pageable pageable);
}
