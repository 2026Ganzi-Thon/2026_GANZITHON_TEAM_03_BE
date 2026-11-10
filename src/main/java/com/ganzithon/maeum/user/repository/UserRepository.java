package com.ganzithon.maeum.user.repository;

import com.ganzithon.maeum.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> { }
