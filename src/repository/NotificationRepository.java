package repository;

import model.Notification;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Notifications persisted to an embedded H2 database via JDBC, using the
 * DAO pattern (see {@link Dao}) — the one entity in the app backed by a real
 * database instead of a CSV file. Every other repository stays CSV-based;
 * converting all seven was judged not worth the risk this close to a
 * deadline, so this one exists specifically to demonstrate real JDBC
 * connectivity rather than to replace the CSV approach everywhere.
 *
 * H2 (not SQLite) because its JDBC driver is a single pure-Java jar with no
 * native libraries to bundle — see lib/h2-*.jar and the README's run
 * instructions, which need that jar on the classpath.
 *
 * The public API here is unchanged from the CSV-backed version it replaced
 * ({@code findByRecipient}, {@code unreadCount}, {@code add}, {@code
 * markAllRead}, plus the constructor's signature), so every caller across
 * the rest of the app needed zero changes.
 */
public class NotificationRepository implements Dao<Notification> {

    private final String jdbcUrl;

    public NotificationRepository(String dataPath) {
        // Reuse the CSV path's base name so the database file sits next to
        // the data it replaces: data/notifications.csv -> jdbc:h2:./data/notifications
        String base = dataPath.replaceFirst("\\.csv$", "");
        this.jdbcUrl = "jdbc:h2:./" + base;
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "H2 JDBC driver not on the classpath. Add lib/h2-*.jar to the classpath: "
                    + "javac -cp \"lib/h2-2.2.224.jar\" ... and java -cp \"out;lib/h2-2.2.224.jar\" ... "
                    + "on the command line, or in IntelliJ, right-click lib/h2-2.2.224.jar in the "
                    + "Project panel and choose \"Add as Library...\".", e);
        }
        createTableIfMissing();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    private void createTableIfMissing() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id VARCHAR(64) PRIMARY KEY,
                    recipient_email VARCHAR(255) NOT NULL,
                    message VARCHAR(1000) NOT NULL,
                    link VARCHAR(255),
                    is_read BOOLEAN NOT NULL,
                    created_at VARCHAR(64) NOT NULL
                )""");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialise the notifications table", e);
        }
    }

    // ------------------------------ Dao<Notification> ------------------------------

    @Override
    public List<Notification> findAll() {
        String sql = "SELECT id, recipient_email, message, link, is_read, created_at FROM notifications";
        List<Notification> out = new ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(fromRow(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read notifications", e);
        }
        return out;
    }

    @Override
    public Optional<Notification> findById(String id) {
        String sql = "SELECT id, recipient_email, message, link, is_read, created_at "
                + "FROM notifications WHERE id = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(fromRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read notification " + id, e);
        }
    }

    @Override
    public void save(Notification n) {
        // MERGE = upsert: insert if the id is new, overwrite if it already
        // exists — matches the CSV version's save-by-id-replace semantics.
        String sql = "MERGE INTO notifications (id, recipient_email, message, link, is_read, created_at) "
                + "KEY (id) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, n.id());
            ps.setString(2, n.recipientEmail());
            ps.setString(3, n.message());
            ps.setString(4, n.link());
            ps.setBoolean(5, n.read());
            ps.setString(6, n.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save notification " + n.id(), e);
        }
    }

    // ------------------------------ domain-specific API every caller already uses ------------------------------

    /** Most recent first. */
    public List<Notification> findByRecipient(String email) {
        String sql = "SELECT id, recipient_email, message, link, is_read, created_at FROM notifications "
                + "WHERE LOWER(recipient_email) = LOWER(?) ORDER BY created_at DESC";
        List<Notification> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(fromRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read notifications for " + email, e);
        }
        return out;
    }

    public long unreadCount(String email) {
        return findByRecipient(email).stream().filter(n -> !n.read()).count();
    }

    public void add(Notification n) {
        save(n);
    }

    public void markAllRead(String email) {
        String sql = "UPDATE notifications SET is_read = TRUE "
                + "WHERE LOWER(recipient_email) = LOWER(?) AND is_read = FALSE";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark notifications read for " + email, e);
        }
    }

    private static Notification fromRow(ResultSet rs) throws SQLException {
        return new Notification(rs.getString("id"), rs.getString("recipient_email"), rs.getString("message"),
                rs.getString("link"), rs.getBoolean("is_read"), rs.getString("created_at"));
    }
}
