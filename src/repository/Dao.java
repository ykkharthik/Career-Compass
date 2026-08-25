package repository;

import java.util.List;
import java.util.Optional;

/**
 * A generic data-access contract: the same shape regardless of what actually
 * stores the data. Every repository in this package is CSV-backed except
 * {@link NotificationRepository}, which implements this over a real
 * database via JDBC — the interface is what lets that swap happen without
 * any caller needing to know or care which storage backend is behind it.
 *
 * @param <T> the entity type this DAO manages
 */
public interface Dao<T> {

    List<T> findAll();

    Optional<T> findById(String id);

    void save(T entity);
}
