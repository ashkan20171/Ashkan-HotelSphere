package com.ashkan.hotel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Stores non-authentication staff details separately from credential data. */
public final class StaffProfileService {
    public record Profile(String username, String fullName, String email, String phone,
                          String department, String jobTitle, String notes) {
        public Profile {
            username = clean(username, 32); fullName = clean(fullName, 100);
            email = clean(email, 120); phone = clean(phone, 40);
            department = clean(department, 80); jobTitle = clean(jobTitle, 80); notes = clean(notes, 500);
            if (username.isBlank()) throw new IllegalArgumentException("Username is required");
            if (!email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
                throw new IllegalArgumentException("Invalid email address");
        }
        private static String clean(String value, int max) {
            String s = value == null ? "" : value.strip().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
            if (s.length() > max) throw new IllegalArgumentException("Field exceeds " + max + " characters");
            return s;
        }
    }

    private final Path file;
    private final Properties data = new Properties();
    public StaffProfileService() { this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "staff-profiles.properties")); }
    public StaffProfileService(Path file) {
        this.file = file;
        try { Files.createDirectories(file.toAbsolutePath().getParent()); if (Files.exists(file)) try (var in = Files.newInputStream(file)) { data.load(in); } }
        catch (IOException e) { throw new IllegalStateException("Cannot load staff profiles", e); }
    }
    private static String key(String username) { return Base64.getUrlEncoder().withoutPadding().encodeToString(username.getBytes(StandardCharsets.UTF_8)); }
    private static String enc(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String value) { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
    public synchronized Optional<Profile> find(String username) {
        String raw = data.getProperty(key(username)); if (raw == null) return Optional.empty();
        try { String[] f = raw.split("\\.", -1); if (f.length != 6) return Optional.empty();
            return Optional.of(new Profile(username, dec(f[0]), dec(f[1]), dec(f[2]), dec(f[3]), dec(f[4]), dec(f[5])));
        } catch (RuntimeException ex) { return Optional.empty(); }
    }
    public synchronized List<Profile> all() {
        List<Profile> result = new ArrayList<>();
        for (String k : data.stringPropertyNames()) try { String user = new String(Base64.getUrlDecoder().decode(k), StandardCharsets.UTF_8); find(user).ifPresent(result::add); } catch (RuntimeException ignored) { }
        result.sort(Comparator.comparing(Profile::username, String.CASE_INSENSITIVE_ORDER)); return List.copyOf(result);
    }
    public synchronized List<Profile> search(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return all().stream().filter(p -> q.isEmpty() || String.join(" ", p.username(), p.fullName(), p.email(), p.phone(), p.department(), p.jobTitle()).toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    public synchronized void save(Profile profile) throws IOException {
        Objects.requireNonNull(profile); String value = String.join(".", enc(profile.fullName()), enc(profile.email()), enc(profile.phone()), enc(profile.department()), enc(profile.jobTitle()), enc(profile.notes()));
        data.setProperty(key(profile.username()), value); persist();
    }
    private void persist() throws IOException {
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "staff-", ".tmp");
        try { try (var out = Files.newOutputStream(temp)) { data.store(out, "HotelSphere staff profiles; credentials are stored separately"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
