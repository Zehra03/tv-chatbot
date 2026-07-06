package com.paximum.paxassist.auth.domain;

/**
 * Authorization role; matches the {@code ck_users_role} check constraint on {@code users.role}.
 */
public enum Role {
    USER,
    ADMIN
}
