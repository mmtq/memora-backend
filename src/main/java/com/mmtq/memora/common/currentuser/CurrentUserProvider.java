package com.mmtq.memora.common.currentuser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserProvider {

    private final UUID currentUserId;

    public CurrentUserProvider( @Value("${app.current-user-id:00000000-0000-0000-0000-000000000001}") String currentUserId ) {
        this.currentUserId = UUID.fromString( currentUserId );
    }

    public UUID getCurrentUserId() {
        return currentUserId;
    }
}
