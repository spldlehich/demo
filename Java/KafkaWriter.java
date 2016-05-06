---
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
---
import com.google.common.net.HostAndPort;
import kafka.common.FailedToSendMessageException;
import kafka.common.KafkaException;
import kafka.common.KafkaStorageException;
import kafka.common.ReplicaNotAvailableException;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Предоставляет возможность записать события в Kafka.
 * <p>
 * Вариант использования: KafkaWriter kafkaWriter = new KafkaWriter(topic, writers, serializer);
 * kafkaWriter.writeEvents(events);
 * 
 * <pre>
 * </pre>
 * <p>
 * <b>Synchronization</b>
 * <p>
 * Экземпляр данного класса является потокобезопасным.
 *
 */
public class KafkaWriter implements IKafkaWriter
{
    private static final String NAME_SERIALIZER_CLASS = "serializer.class";
    private static final String SERIALIZER_CLASS = "kafka.serializer.StringEncoder";
    private static final String NAME_BROKER_LIST = "metadata.broker.list";
    private final ISerializer serializer;

    /**
     * Карта, где ключ - нужный хост и раздел временного хранилища, значение - только адресс хоста(раздел не нужен).
     */
    private final Map<Shard, String> routeMap = new HashMap<>();
    /**
     * Карта, где ключ - адрес временного хранилища, значение - продюсер.
     */
    private final Map<String, Producer<String, String>> producersMap = new HashMap<>();
    private final String topic;
    private final WritersSelector writers;

    /**
     * Конструктор.
     * 
     * @param topic топик
     * @param part партиция
     * @param serializer сериализатор
     */
    public KafkaWriter(String topic, WritersSelector writers, ISerializer serializer)
    {
        checkArgument(topic != null);
        checkArgument(writers != null);
        checkArgument(serializer != null);
        this.serializer = serializer;
        this.topic = topic;
        this.writers = writers;
    }

    @Override
    public void close()
    {
        cleanProducers();
    }

    @Override
    public boolean writeEvents(ClusterEvents events, int id) throws KafkaException
    {
        checkRoute();
        List<IssuedEvent> listEvent = events.getEvents();
        if (!listEvent.isEmpty())
        {
            Shard info = writers.selectShard(id);
            try
            {
                if (info == null)
                    throw new KafkaException(Messages.not_available());
                Producer<String, String> producer = getProducer(info);
                String  eventSerialized = serializer.serialize(events);
                producer.send(new KeyedMessage<String, String>(topic, String.valueOf(info.getShardNumber()), eventSerialized));
            }
            catch (SerializeException e)
            {
                throw new KafkaException(Messages.failed_write_to_storage(), e);
            }
            catch (KafkaStorageException | FailedToSendMessageException | ReplicaNotAvailableException e)
            {
                removeProducer(info);
                throw new KafkaException(Messages.failed_write_to_storage(), e);
            }
            return true;
        }
        return false;
    }

    private synchronized void checkRoute()
    {
        if (!VerifyRoute.verify(routeMap.size(), writers.getShardsCount()))
            cleanProducers();
    }

    private synchronized void removeProducer(Shard info)
    {
        if (info == null)
            return;
        String address = routeMap.get(info);
        if (address == null)
            return;
        producersMap.remove(address);
        routeMap.remove(info);
    }

    private synchronized Producer<String, String> getProducer(Shard info)
    {
        HostAndPort hap = info.getReceiver().getAddress();
        checkArgument(hap != null, "Address can not be null");
        String address = routeMap.computeIfAbsent(info, v -> hap.toString());
        return producersMap.computeIfAbsent(hap.toString(), v -> createProducer(address));
    }

    private Producer<String, String> createProducer(String broker)
    {
        Properties kafkaProducerConfig = new Properties();
        kafkaProducerConfig.put(NAME_SERIALIZER_CLASS, SERIALIZER_CLASS);
        kafkaProducerConfig.put(NAME_BROKER_LIST, broker);
        return new Producer<String, String>(new ProducerConfig(kafkaProducerConfig));
    }

    private synchronized void cleanProducers()
    {
        for (Entry<String, Producer<String, String>> entry : producersMap.entrySet())
        {
            // Судя по коду, Producer потокобезопасен.
            entry.getValue().close();
        }
        producersMap.clear();
        routeMap.clear();
    }

    @Localizable
    interface IMessagesList
    {
        IMessagesList Messages = LocalizableFactory.create(IMessagesList.class);

        @DefaultString("Events write error.")
        @Context("В случае ошибки записи событий во временное хранилище.")
        @Tags({"logs"})
        String failed_write_to_storage();

        @DefaultString("Temporary storage not available.")
        @Context("Если нет доступных временных хранилищ.")
        @Tags({"logs"})
        String not_available();
    }
}
