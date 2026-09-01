// by Jeremy Posada
package com.jposada.anaquel.infrastructure.persistence;

import com.jposada.anaquel.domain.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from AppUser u where u.blockedUntil is not null and u.blockedUntil > :now order by u.blockedUntil desc")
    List<AppUser> findCurrentlyBlocked(Instant now);

    @Query("select count(u) from AppUser u where u.blockedUntil is not null and u.blockedUntil > :now")
    long countCurrentlyBlocked(Instant now);
}
