package com.gateway.authservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

class UserStoreTest {

    private final UserStore userStore = new UserStore();

    @Test
    void isValid_returnsTrueForCorrectCredentials() {
        assertTrue(userStore.isValid("seema", "password123"));
    }

    @Test
    void isValid_returnsFalseForWrongPassword() {
        assertFalse(userStore.isValid("seema", "wrong-password"));
    }

    @Test
    void isValid_returnsFalseForUnknownUser() {
        assertFalse(userStore.isValid("nobody", "anything"));
    }

    @Test
    void rolesOf_returnsAdminAndUserForSeema() {
        assertThat(userStore.rolesOf("seema")).containsExactly("USER", "ADMIN");
    }

    @Test
    void rolesOf_returnsEmptyListForUnknownUser() {
        assertThat(userStore.rolesOf("nobody")).isEmpty();
    }
}