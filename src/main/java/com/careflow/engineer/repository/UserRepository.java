package com.careflow.engineer.repository;

import com.careflow.engineer.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
