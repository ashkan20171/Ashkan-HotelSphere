package com.ashkan.hotel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class AuthServiceSecuritySmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("hotelsphere-auth-test-");
        try {
            AuthService auth = new AuthService(dir);
            check(auth.isActive("Admin"), "default administrator enabled");
            for (int i = 0; i < 4; i++) {
                check(auth.authenticate("Admin", "wrong-password".toCharArray()) == AuthService.LoginResult.INVALID_CREDENTIALS,
                        "failed attempt " + (i + 1));
            }
            check(auth.authenticate("Admin", "wrong-password".toCharArray()) == AuthService.LoginResult.LOCKED,
                    "fifth failed attempt locks account");
            check(auth.authenticate("Admin", "admin123".toCharArray()) == AuthService.LoginResult.LOCKED,
                    "correct password cannot bypass active lockout");

            Path log = dir.resolve("security-events.tsv");
            String events = Files.readString(log);
            check(events.contains("LOGIN_LOCKED_5_MINUTES"), "lockout logged");
            check(!events.contains("wrong-password"), "passwords are never logged");

            boolean lastAdminProtected = false;
            try { auth.setActive("Admin", "Admin", false); }
            catch (IllegalArgumentException expected) { lastAdminProtected = true; }
            check(lastAdminProtected, "cannot disable self or last administrator");

            auth.setActive("Admin", "Receptionist", false);
            check(!auth.isActive("Receptionist"), "receptionist disabled");
            check(auth.authenticate("Receptionist", "desk123".toCharArray()) == AuthService.LoginResult.DISABLED,
                    "disabled account cannot log in");
            auth.setActive("Admin", "Receptionist", true);
            check(auth.isActive("Receptionist"), "receptionist re-enabled");

            AuthService reloaded = new AuthService(dir);
            check(reloaded.isActive("Receptionist"), "active state persists after reload");
            check(reloaded.lockoutRemainingMillis("Admin", System.currentTimeMillis()) > 0,
                    "lockout persists after reload");
            check(Files.readString(log).contains("LOGIN_REJECTED_DISABLED_ACCOUNT"), "disabled login attempt logged");
            System.out.println("AuthServiceSecuritySmokeTest PASSED");
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
