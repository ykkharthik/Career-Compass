package web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory session store: random token (cookie) -> logged-in user's email,
 * plus a per-session CSRF secret and idle-timeout tracking.
 *
 * Sessions live only in this process's memory (documented limitation: a
 * server restart logs everyone out — acceptable for a course/demo deployment,
 * called out explicitly rather than silently, since a production system
 * would back this with persistent, shared session storage).
 *
 * A daemon background thread sweeps out sessions that went idle past the
 * timeout but were never touched again (so {@link #emailFor} never got a
 * chance to evict them lazily) — otherwise an abandoned session (someone
 * closes the tab without logging out) sits in these maps forever.
 */
public class SessionManager {

    private static final long IDLE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes
    private static final long SWEEP_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes

    private final Map<String, String> emailByToken = new ConcurrentHashMap<>();
    private final Map<String, String> csrfByToken = new ConcurrentHashMap<>();
    private final Map<String, Long> lastActivity = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public SessionManager() {
        Thread sweeper = new Thread(this::sweepLoop, "session-sweeper");
        // Daemon: this thread must never keep the JVM alive on its own — if
        // every other (non-daemon) thread finishes, the process should be
        // free to exit instead of hanging on a housekeeping loop.
        sweeper.setDaemon(true);
        sweeper.start();
    }

    private void sweepLoop() {
        while (true) {
            try {
                TimeUnit.MILLISECONDS.sleep(SWEEP_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long now = System.currentTimeMillis();
            for (var entry : lastActivity.entrySet()) {
                if (now - entry.getValue() > IDLE_TIMEOUT_MS) destroy(entry.getKey());
            }
        }
    }

    public String create(String email) {
        String token = randomToken(24);
        emailByToken.put(token, email);
        csrfByToken.put(token, randomToken(24));
        lastActivity.put(token, System.currentTimeMillis());
        return token;
    }

    /**
     * Returns the signed-in email for this token, or null if there is no
     * such session or it has gone idle past the timeout (in which case it is
     * destroyed as a side effect). Touches (extends) the session on success.
     */
    public String emailFor(String token) {
        if (token == null) return null;
        Long last = lastActivity.get(token);
        if (last == null) return null;
        if (System.currentTimeMillis() - last > IDLE_TIMEOUT_MS) {
            destroy(token);
            return null;
        }
        lastActivity.put(token, System.currentTimeMillis());
        return emailByToken.get(token);
    }

    public String csrfFor(String token) {
        return token == null ? null : csrfByToken.get(token);
    }

    public void destroy(String token) {
        if (token == null) return;
        emailByToken.remove(token);
        csrfByToken.remove(token);
        lastActivity.remove(token);
    }

    private String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
