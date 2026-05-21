package com.coha9nus.kenreserve.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    List<User> findByRole(Role role);

    /** tutors に指定の講師が含まれる生徒を取得する */
    List<User> findByTutors_Id(Long tutorId);
}
