package com.the.weather.user.repository;

import java.util.Optional;

import com.the.weather.user.model.User;

public interface UserRepository {

    Optional<User> findByEmail(String normalizedEmail);

    boolean saveIfEmailAvailable(User user);
}
