package repository;

import model.Notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NotificationRepository {
    private final String dataPath;
    private final List<Notification> notifications = new ArrayList<>();

    public NotificationRepository(String dataPath) {
        this.dataPath = dataPath;
        for (String line : FileManager.readLines(dataPath)) {
            try { notifications.add(Notification.fromCsv(line)); } catch (RuntimeException ignored) { }
        }
    }

    /** Most recent first. */
    public List<Notification> findByRecipient(String email) {
        return notifications.stream()
                .filter(n -> n.recipientEmail().equalsIgnoreCase(email))
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .toList();
    }

    public long unreadCount(String email) {
        return findByRecipient(email).stream().filter(n -> !n.read()).count();
    }

    public void add(Notification n) {
        notifications.add(n);
        FileManager.appendLine(dataPath, n.toCsv());
    }

    public void markAllRead(String email) {
        boolean changed = false;
        for (int i = 0; i < notifications.size(); i++) {
            Notification n = notifications.get(i);
            if (n.recipientEmail().equalsIgnoreCase(email) && !n.read()) {
                notifications.set(i, n.asRead());
                changed = true;
            }
        }
        if (changed) persist();
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Notification n : notifications) lines.add(n.toCsv());
        FileManager.writeLines(dataPath, lines);
    }
}
