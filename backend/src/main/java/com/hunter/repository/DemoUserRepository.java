package com.hunter.repository;

import com.hunter.model.DemoUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemoUserRepository extends JpaRepository<DemoUser, String> {
    Optional<DemoUser> findByUsername(String username);
}
