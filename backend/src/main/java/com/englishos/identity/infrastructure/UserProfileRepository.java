package com.englishos.identity.infrastructure;

import com.englishos.identity.domain.UserProfile;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
