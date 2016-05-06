---
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
---
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Предоставляет возможности поиска, удаления и
 * записи событий в хранилище.
 * <p>
 * <b>Synchronization</b>
 * <p>
 * Экземпляр данного класса не является потокобезопасным.
 *
 * @see IJournalService
 * @see NativeStorageReader
 * @see NativeStorageWriter
 * @see RestStorageReader
 * @see KafkaStorageWriter
 */
public class DefaultJournalService implements IJournalService
{
    private final IStorageWriter writer;
    private final IStorageReader reader;

    /**
     * Создает сервис в смешанном варианте.
     * @param kafkaWriter писатель во временное хранилище Kafka
     * @param servers список серверов БД журнала регистрации
     * @return сервис
     */
    public static IJournalService createMixed(IKafkaWriter kafkaWriter, List<InetSocketAddress> servers)
    {
        return new DefaultJournalService(new KafkaStorageWriter(kafkaWriter), new NativeStorageReader(new MongoStorage(
                new MongoDatabaseCollections(servers), false)));
    }

    private DefaultJournalService(IStorageWriter writer, IStorageReader reader)
    {
        this.writer = writer;
        this.reader = reader;
    }

    @Override
    public ResponseEvents fetchEventsByFilters(String ibName, LogEntriesFilterRequest filters) throws JournalException
    {
        checkArgument(filters != null);
        try
        {
            ResponseEvents events = new ResponseEvents();
            ResponseStorage res = reader.fetchEventsByFilters(ibName, filters);
            if (res == null)
                return null;
            events.setEvents(res.getData());
            return events;
        }
        catch (TaskException e)
        {
            throw new JournalException(Messages.serviceFetchException(), e);
        }
    }

    @Override
    public boolean deleteEventsByFilterList(String ibName, LogEntriesFilterRequest filters) throws JournalException
    {
        checkArgument(filters != null);
        try
        {
            return reader.deleteEventsByFilterList(ibName, filters);
        }
        catch (TaskException e)
        {
            throw new JournalException(Messages.serviceDeleteException(), e);
        }
    }

    @Override
    public LoggedParams loggedParams(String ibName, Set<Splitter> splitters, Collection<String> fields)
            throws JournalException
    {
        checkArgument(ibName != null);
        try
        {
            return reader.loggedParams(ibName, splitters, fields);
        }
        catch (TaskException e)
        {
            throw new JournalException(Messages.serviceLoggedException(), e);
        }
    }

    @Override
    public void writeEvents(ClusterEvents events, int id) throws JournalException
    {
        writeClusterEvents(events, id);
    }

    @Override
    public void writeEvent(String clusterName, IssuedEvent event, int id) throws JournalException
    {
        checkArgument(event != null);
        List<IssuedEvent> list = new ArrayList<>();
        list.add(event);
        ClusterEvents events = new ClusterEvents(clusterName, list);
        writeClusterEvents(events, id);
    }

    @Override
    public void close()
    {
        writer.close();
        reader.close();
    }

    private void writeClusterEvents(ClusterEvents events, int id) throws JournalException
    {
        checkArgument(events != null);
        try
        {
            writer.writeEvents(events, id);
        }
        catch (TaskException e)
        {
            throw new JournalException(Messages.serviceWriteException(), e);
        }
    }

    @Localizable
    interface IMessagesList
    {
        IMessagesList Messages = LocalizableFactory.create(IMessagesList.class);

        @DefaultString("Cannot get events.")
        @Context("Ошибка сервиса при получении событий.")
        @Tags({"logs"})
        String serviceFetchException();

        @DefaultString("Cannot delete events.")
        @Context("Ошибка сервиса при удалении событий.")
        @Tags({"logs"})
        String serviceDeleteException();

        @DefaultString("Cannot get metadata events.")
        @Context("Ошибка сервиса при получении метаданных.")
        @Tags({"logs"})
        String serviceLoggedException();

        @DefaultString("Cannot put events.")
        @Context("Ошибка сервиса при записи событий.")
        @Tags({"logs"})
        String serviceWriteException();
    }
}
