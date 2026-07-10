package org.microsoft.qintelipass.repository;

import jakarta.persistence.LockModeType;
import org.microsoft.qintelipass.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByPhone(String phone);
    Optional<User> findByName(String name);
    Optional<User> findByEmail(String email);
    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByPhoneAndIdNot(String phone, Long id);
}
