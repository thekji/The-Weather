package com.the.weather.security;

import java.security.Principal;

public record AuthenticatedUser(String userID, String username) implements Principal {

    @Override
    public String getName() {
        return userID;
    }
}
