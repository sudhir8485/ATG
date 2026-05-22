package com.nt.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.nt.entity.User;

public interface UserRepository extends JpaRepository<User,Integer>{

List<User> findByRole(String role);
List<User> findByRoleAndDeletedFalse(String role);

List<User> findByUsernameAndPassword(String username,String password);

List<User> findByEmailAndPassword(String email,String password);

User findByName(String name);

User findByUsernameIgnoreCase(String username);

boolean existsByUsernameIgnoreCase(String username);

List<User> findByDeletedFalse();
List<User> findByDeletedTrue();

long countByRole(String role);

}
