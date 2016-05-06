---
import java.io.Closeable;
import java.util.Collection;
import java.util.Set;
---

/**
 * <code>IStorageReader</code> интерфейс для выборки событий из хранилища/данных "легенды".
 * <p>
 * 
 */
public interface IStorageReader extends Closeable
{
    /**
     * Возвращает события по заданным условиям поиска.
     * 
     * @param ibName имя ИБ
     * @param filters условия поиска
     * @return события
     * @throws TaskException исключение при ошибке выборки
     * сервиса)
     */
    public ResponseStorage fetchEventsByFilters(String ibName, LogEntriesFilterRequest filters) throws TaskException;

    /**
     * Удаляет события по заданным условиям выборки.
     * 
     * @param ibName имя ИБ
     * @param filters условия поиска
     * @return результат выполнения
     * @throws TaskException исключение при ошибке удаления
     * сервиса)
     */
    public boolean deleteEventsByFilterList(String ibName, LogEntriesFilterRequest filters) throws TaskException;

    /**
     * Возвращает данные легенды
     * 
     * @param ibName имя ИБ
     * @param splitters разделители
     * @param fields поля
     * @return данные
     * @throws TaskException исключение при ошибке выгрузки легенды
     */
    public LoggedParams loggedParams(String ibName, Set<Splitter> splitters, Collection<String> fields)
            throws TaskException;

    @Override
    public void close();
}
