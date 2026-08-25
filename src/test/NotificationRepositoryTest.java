package test;

import model.Notification;
import repository.NotificationRepository;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

/**
 * Exercises the one JDBC/H2-backed repository in the app. Each test uses its
 * own scratch database file (never data/notifications.*) so tests can't
 * interfere with each other or with real notification data, and cleans up
 * after itself even if an assertion fails.
 */
public class NotificationRepositoryTest {

    public void testSaveAndFindByRecipient() {
        withScratchDb("save_find", path -> {
            NotificationRepository repo = new NotificationRepository(path);
            String id = UUID.randomUUID().toString();
            repo.add(new Notification(id, "student@example.com", "Test message", "/student",
                    false, Instant.now().toString()));

            var found = repo.findByRecipient("student@example.com");
            Assert.equal("one notification should be found", 1, found.size());
            Assert.equal("message round-trips through the database", "Test message", found.get(0).message());
            Assert.isFalse("recipient lookup is case-insensitive",
                    repo.findByRecipient("STUDENT@EXAMPLE.COM").isEmpty());
        });
    }

    public void testUnreadCountAndMarkAllRead() {
        withScratchDb("mark_read", path -> {
            NotificationRepository repo = new NotificationRepository(path);
            String email = "student2@example.com";
            repo.add(new Notification(UUID.randomUUID().toString(), email, "One", "/", false, Instant.now().toString()));
            repo.add(new Notification(UUID.randomUUID().toString(), email, "Two", "/", false, Instant.now().toString()));

            Assert.equal("two unread notifications", 2L, repo.unreadCount(email));
            repo.markAllRead(email);
            Assert.equal("zero unread after markAllRead", 0L, repo.unreadCount(email));
        });
    }

    public void testDaoFindByIdAndFindAll() {
        withScratchDb("dao", path -> {
            NotificationRepository repo = new NotificationRepository(path);
            String id = UUID.randomUUID().toString();
            repo.save(new Notification(id, "student3@example.com", "Via Dao.save", "/", false,
                    Instant.now().toString()));

            Assert.isTrue("Dao.findById finds the saved row", repo.findById(id).isPresent());
            Assert.isTrue("Dao.findAll includes it", repo.findAll().stream().anyMatch(n -> n.id().equals(id)));
            Assert.isTrue("Dao.findById returns empty for an unknown id",
                    repo.findById("does-not-exist").isEmpty());
        });
    }

    private interface ScratchTest { void run(String scratchDbPath); }

    private void withScratchDb(String name, ScratchTest test) {
        String path = "data/.test_notifications_" + name + ".csv"; // NotificationRepository strips .csv -> .mv.db
        String dbFile = "data/.test_notifications_" + name + ".mv.db";
        try {
            test.run(path);
        } finally {
            new File(dbFile).delete();
        }
    }
}
