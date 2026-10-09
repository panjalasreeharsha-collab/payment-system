package com.example.paymentsystem.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.example.paymentsystem.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

        @Query("select account from User account "
            + "where lower(trim(account.upiId)) = lower(trim(:upiId))")
        Optional<User> findByUpiIdIgnoreCase(@Param("upiId") String upiId);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByUpiIdIgnoreCase(String upiId);
}