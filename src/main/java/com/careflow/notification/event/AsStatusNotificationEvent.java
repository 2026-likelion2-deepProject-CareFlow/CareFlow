package com.careflow.notification.event;

import com.careflow.user.entity.User;

public record AsStatusNotificationEvent(
        User receiver,
        String title,
        String body
) {}