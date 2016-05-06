---
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Содержит метаинформацию для кластерных событий: источник, позиция источника. Источник = адресу временного хранилища. 
 */
public final class MetaClusterEvent
{
    private final MetaFrom from;
    private final long position;

    /**
     * создает экземпляр с метаинформациех
     *
     * @param host хост временного хранилища
     * @param port порт временного хранилища
     * @param shardNumber раздел временного хранилища
     * @param position позиция для определенного номера раздела временного хранилища
     * @return экземпляр MetaClusterEvent
     */
    public static MetaClusterEvent instanceOf(String host, int port, int shardNumber, long position)
    {
        MetaFrom from = new MetaFrom(host, port, shardNumber);
        return new MetaClusterEvent(from, position);
    }

    /**
     * Конструктор
     */
    private MetaClusterEvent(MetaFrom from, long position)
    {
        checkArgument(from != null);
        this.from = from;
        this.position = position;
    }

    @Override
    public String toString()
    {
        return from.getFromString() + "_" + position;
    }

    /**
     * Возврашает метаинформацию о временном хранилище.
     *
     * @return метаинформация о временном хранилище
     */
    public MetaFrom getFrom()
    {
        return from;
    }

    /**
     * Возвращает позицию для заданного временного хранилища.
     *
     * @return позиция
     */
    public long getPosition()
    {
        return position;
    }

}
