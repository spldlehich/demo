---
import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * <code>ClusterEvents</code> класс-обертка для событий произошедших внутри одной ИБ.
 *
 * <p>
 * Вариант использования:
 * 
 * <pre>
 * ClusterEvents storage = new ClusterEvents(ibName, events);
 * </pre>
 * <p>
 * <b>Synchronization</b>
 * <p>
 * Экземпляр данного класса не является потокобезопасным.
 *
 */
public class ClusterEvents
{
    private String ibName;
    private List<IssuedEvent> events;

    /**
     * Конструктор
     * 
     * @param ibName соответствует имени информационной базы.
     * @param events события
     */
    @JsonCreator
    public ClusterEvents(@JsonProperty("ibName") String ibName,
            @JsonProperty("events") List<IssuedEvent> events)
    {
        checkArgument(ibName != null);
        checkArgument(events != null);
        this.ibName = ibName;
        this.events = ImmutableList.copyOf(events);
    }

    /**
     * Возвращает имя кластера
     *
     * @return имя
     */
    public String getIbName()
    {
        return ibName;
    }

    /**
     * Возвращает события
     *
     * @return события
     */
    public List<IssuedEvent> getEvents()
    {
        return events;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ibName.hashCode();
        result = prime * result + events.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClusterEvents other = (ClusterEvents)obj;
        if (!ibName.equals(other.ibName))
            return false;
        if (!events.equals(other.events))
            return false;
        return true;
    }

}
