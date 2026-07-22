package com.ir.portfolio.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the acting user. V1 is single-user: always the seeded default user.
 * The schema already scopes every asset/order by user_id, so adding real
 * authentication later only requires changing this resolution.
 */
@Component
public class UserContext {

    public static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public UUID currentUserId() {
        return DEFAULT_USER_ID;
    }
}
