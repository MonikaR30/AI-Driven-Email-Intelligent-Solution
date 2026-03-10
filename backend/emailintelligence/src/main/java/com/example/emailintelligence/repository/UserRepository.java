package com.example.emailintelligence.repository;

import java.util.List;
import com.example.emailintelligence.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findAllByEmail(String email);

}
