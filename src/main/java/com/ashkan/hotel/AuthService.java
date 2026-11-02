package com.ashkan.hotel;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** Local account verifier. Each account is salted and hashed using PBKDF2-HMAC-SHA256. */
public final class AuthService {
    private static final int ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    public enum LoginResult { SUCCESS, INVALID_CREDENTIALS, LOCKED, DISABLED }
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MILLIS = 5 * 60 * 1000L;
    private final Path credentials;
    private final Properties users = new Properties();
    private final Properties roles = new Properties();
    private final Properties active = new Properties();
    private final Properties failures = new Properties();

    public AuthService() { this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere")); }

    /** Directory-injectable constructor keeps security behavior testable without touching the real profile. */
    public AuthService(Path directory) {
        this.credentials = directory.resolve("accounts.properties");
        try {
            Files.createDirectories(credentials.getParent());
            if (Files.exists(credentials)) {
                try (var in = Files.newInputStream(credentials)) { users.load(in); }
                loadRoles();
                loadProperties(activeFile(), active);
                loadProperties(failuresFile(), failures);
            } else {
                // Demo-only first-run accounts; change them before using real guest information.
                setPassword("Admin", "admin123".toCharArray());
                setPassword("Receptionist", "desk123".toCharArray());
                roles.setProperty("Admin", "Admin");
                roles.setProperty("Receptionist", "Receptionist");
                active.setProperty("Admin", "true");
                active.setProperty("Receptionist", "true");
                persist();
            }
            // Accounts added by older versions remain enabled unless explicitly disabled.
            for (String username : users.stringPropertyNames()) active.putIfAbsent(username, "true");
            persist();
        } catch (IOException e) { throw new IllegalStateException("Cannot initialize accounts", e); }
    }

    private Path roleFile() { return credentials.resolveSibling("roles.properties"); }
    private Path activeFile() { return credentials.resolveSibling("active.properties"); }
    private Path failuresFile() { return credentials.resolveSibling("login-security.properties"); }
    private Path securityLogFile() { return credentials.resolveSibling("security-events.tsv"); }
    private void loadProperties(Path path, Properties target) throws IOException {
        if (Files.exists(path)) try (var in = Files.newInputStream(path)) { target.load(in); }
    }
    private void saveProperties(Path path, Properties properties, String header) throws IOException {
        Path temp = Files.createTempFile(credentials.getParent(), "security-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { properties.store(out, header); }
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private void securityEvent(String username, String event) {
        try {
            if (!Files.exists(securityLogFile())) Files.writeString(securityLogFile(), "timestamp_utc\tusername\tevent\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            String safeUser = username == null ? "<unknown>" : username.replace("\t", " ").replace("\n", " ").replace("\r", " ");
            String safeEvent = event.replace("\t", " ").replace("\n", " ").replace("\r", " ");
            Files.writeString(securityLogFile(), java.time.Instant.now() + "\t" + safeUser + "\t" + safeEvent + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ex) { throw new IllegalStateException("Cannot write security event log", ex); }
    }
    private void loadRoles() throws IOException {
        if (Files.exists(roleFile())) try (var in = Files.newInputStream(roleFile())) { roles.load(in); }
        // Preserve compatibility with the two original demonstration accounts.
        for (String username : users.stringPropertyNames())
            if (!roles.containsKey(username)) roles.setProperty(username, "Admin".equals(username) ? "Admin" : "Receptionist");
    }
    public synchronized String roleOf(String username) { return roles.getProperty(username, "Receptionist"); }
    public synchronized java.util.List<String> accounts() {
        return users.stringPropertyNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    public synchronized boolean isActive(String username) { return users.containsKey(username) && Boolean.parseBoolean(active.getProperty(username, "true")); }
    public synchronized void setActive(String actor, String username, boolean enabled) throws IOException {
        requireAdmin(actor);
        if (!users.containsKey(username)) throw new IllegalArgumentException("Account not found");
        if (username.equals(actor) && !enabled) throw new IllegalArgumentException("You cannot disable your own account");
        if (!enabled && isActive(username) && "Admin".equals(roleOf(username)) && activeAdminCount() <= 1)
            throw new IllegalArgumentException("Cannot disable the last active administrator");
        active.setProperty(username, Boolean.toString(enabled));
        persist();
        securityEvent(actor, (enabled ? "ACCOUNT_ENABLED:" : "ACCOUNT_DISABLED:") + username);
    }
    private long activeAdminCount() { return users.stringPropertyNames().stream().filter(u -> isActive(u) && "Admin".equals(roleOf(u))).count(); }
    public synchronized long lockoutRemainingMillis(String username, long nowMillis) {
        String[] state = failures.getProperty(username, "0:0").split(":", -1);
        if (state.length != 2) return 0;
        try { return Math.max(0, Long.parseLong(state[1]) - nowMillis); } catch (NumberFormatException ex) { return 0; }
    }
    public synchronized LoginResult authenticate(String username, char[] password) {
        String user = username == null ? "" : username.trim();
        long now = System.currentTimeMillis();
        if (!users.containsKey(user)) {
            securityEvent(user, "LOGIN_FAILED_UNKNOWN_ACCOUNT");
            return LoginResult.INVALID_CREDENTIALS;
        }
        if (!isActive(user)) { securityEvent(user, "LOGIN_REJECTED_DISABLED_ACCOUNT"); return LoginResult.DISABLED; }
        if (lockoutRemainingMillis(user, now) > 0) { securityEvent(user, "LOGIN_REJECTED_LOCKED"); return LoginResult.LOCKED; }
        String priorState = failures.getProperty(user, "0:0");
        String[] priorFields = priorState.split(":", -1);
        if (priorFields.length == 2) {
            try {
                if (Long.parseLong(priorFields[1]) > 0 && Long.parseLong(priorFields[1]) <= now) {
                    failures.setProperty(user, "0:0");
                }
            } catch (NumberFormatException ignored) { failures.setProperty(user, "0:0"); }
        }
        if (verify(user, password)) {
            failures.remove(user);
            try { saveProperties(failuresFile(), failures, "Failed login counters and lockout expiry timestamps"); }
            catch (IOException ex) { throw new IllegalStateException("Cannot save login security state", ex); }
            securityEvent(user, "LOGIN_SUCCESS");
            return LoginResult.SUCCESS;
        }
        String[] state = failures.getProperty(user, "0:0").split(":", -1);
        int count = 0;
        try { count = Integer.parseInt(state[0]); } catch (Exception ignored) { }
        count++;
        long lockedUntil = count >= MAX_FAILED_ATTEMPTS ? now + LOCKOUT_MILLIS : 0;
        failures.setProperty(user, count + ":" + lockedUntil);
        try { saveProperties(failuresFile(), failures, "Failed login counters and lockout expiry timestamps"); }
        catch (IOException ex) { throw new IllegalStateException("Cannot save login security state", ex); }
        securityEvent(user, lockedUntil > now ? "LOGIN_LOCKED_5_MINUTES" : "LOGIN_FAILED");
        return lockedUntil > now ? LoginResult.LOCKED : LoginResult.INVALID_CREDENTIALS;
    }
    public synchronized void recordSignOut(String username) { securityEvent(username, "SIGN_OUT"); }
    public synchronized java.util.List<String> securityEventsFor(String username) {
        if (username == null || username.isBlank()) return java.util.List.of();
        Path log = securityLogFile();
        if (!Files.exists(log)) return java.util.List.of();
        try {
            return Files.readAllLines(log, StandardCharsets.UTF_8).stream().skip(1)
                    .filter(line -> { String[] fields = line.split("\\t", 3); return fields.length == 3 && fields[1].equals(username); })
                    .toList();
        } catch (IOException ex) { throw new IllegalStateException("Cannot read security event log", ex); }
    }
    public synchronized void createAccount(String actor, String username, char[] password, String role) throws IOException {
        requireAdmin(actor);
        if (username == null || !username.matches("[A-Za-z][A-Za-z0-9_.-]{2,31}")) throw new IllegalArgumentException("Username must be 3-32 ASCII characters");
        if (users.containsKey(username)) throw new IllegalArgumentException("Account already exists");
        if (!"Admin".equals(role) && !"Receptionist".equals(role)) throw new IllegalArgumentException("Invalid role");
        if (password == null || password.length < 12) throw new IllegalArgumentException("Password requires at least 12 characters");
        setPassword(username, password); roles.setProperty(username, role); active.setProperty(username, "true"); failures.remove(username); persist();
    }
    public synchronized void setRole(String actor, String username, String role) throws IOException {
        requireAdmin(actor);
        if (!users.containsKey(username)) throw new IllegalArgumentException("Account not found");
        if (!"Admin".equals(role) && !"Receptionist".equals(role)) throw new IllegalArgumentException("Invalid role");
        if (isActive(username) && "Admin".equals(roleOf(username)) && !"Admin".equals(role) && activeAdminCount() <= 1)
            throw new IllegalArgumentException("Cannot demote the last active administrator");
        roles.setProperty(username, role); persist();
    }
    private long adminCount() { return users.stringPropertyNames().stream().filter(u -> "Admin".equals(roleOf(u))).count(); }
    private void requireAdmin(String actor) {
        if (!users.containsKey(actor) || !"Admin".equals(roleOf(actor))) throw new SecurityException("Administrator required");
    }
    public synchronized boolean verify(String username, char[] password) {
        String encoded = users.getProperty(username);
        if (encoded == null) return false;
        try {
            String[] fields = encoded.split(":", -1);
            if (fields.length != 3) return false;
            int iterations = Integer.parseInt(fields[0]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(fields[1]);
            byte[] expected = Base64.getDecoder().decode(fields[2]);
            byte[] actual = hash(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) { return false; }
    }

    public void changePassword(String username, char[] oldPassword, char[] newPassword) throws IOException {
        if (!verify(username, oldPassword)) throw new IllegalArgumentException("Current password is incorrect");
        if (newPassword.length < 12) throw new IllegalArgumentException("New password must contain at least 12 characters");
        setPassword(username, newPassword);
        persist();
    }

    private void setPassword(String username, char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        users.setProperty(username, ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" +
                Base64.getEncoder().encodeToString(hash(password, salt, ITERATIONS)));
    }

    private static byte[] hash(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("PBKDF2 unavailable", e); }
        finally { spec.clearPassword(); }
    }

    private void persist() throws IOException {
        Path temp = Files.createTempFile(credentials.getParent(), "accounts-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { users.store(out, "HotelSphere local accounts"); }
            try { Files.move(temp, credentials, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, credentials, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        Path rolesTemp = Files.createTempFile(credentials.getParent(), "roles-", ".tmp");
        try {
            try (var out = Files.newOutputStream(rolesTemp)) { roles.store(out, "HotelSphere account roles"); }
            try { Files.move(rolesTemp, roleFile(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(rolesTemp, roleFile(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(rolesTemp); }
        saveProperties(activeFile(), active, "HotelSphere account activation status");
    }
}
