---
import java.io.Closeable;
---

/**
 * интерфейс загрузки событий в хранилища.
 * <p>
 * 
 */
public interface IStorageWriter extends Closeable
{
    /**
     * Сохраняет события в хранилище. <b>id</b> - условный идентификатор для распределения нагрузки. Чтобы не нарущать
     * порядок событий, для каждого пользователя(потока событий от пользователя) должен быть свой идентификатор(не
     * обязательно уникальный) в пределах сессии.
     * 
     * @param events события
     * @param id условный идентификатор для распределения нагрузки
     * @throws WriteStorageException
     * @throws TaskException возникает при ошибке записи в хранилище
     */
    public void writeEvents(ClusterEvents events, int id) throws TaskException;

    @Override
    public void close();
}
