---
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import kafka.common.KafkaException;
---
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Запускает обработчики сообщений из Kafka. Представляет из себя ридеры для разделов из временного хранилища.
 *
 * <p>
 * Вариант использования:
 * 
 * <pre>
 * KafkaReader reader = new KafkaReader(propsKafka, topicName, countThread, serializedFactory);
 * reader.begin(brokers, shard, bufController);
 * </pre>
 *
 * <p>
 * <b>Synchronization</b>
 * <p>
 * Экземпляр данного класса является потокобезопасным.
 *
 * @see IKafkaReader
 * @see KafkaWorker
 */
public class KafkaReader implements IKafkaReader
{
    /**
     * Тайм-аут для scheduleWithFixedDelay(читатели разделов) 
     */
    private static final int TIMEOUT = 1;
    private final Properties propsKafka;
    private final String topicName;
    private final ISerializerFactory serializedFactory;
    private final ScheduledThreadPoolExecutor serviceKafka;
    private final Map<Shard, KafkaShardWorker> workers = new HashMap<>();
    private final Map<Shard, ScheduledFuture<?>> processes = new HashMap<>();
    private final int maxCountThread;

    /**
     * Конструктор. Инициализирует потоки для обработки сообщений
     * 
     * @param propsKafka настройки Kafka
     * @param topicName имя топика
     * @param countThread количество потоков для чтения сообщений
     * @param serializedFactory фабрика сериализатора
     */
    public KafkaReader(Properties propsKafka, String topicName, int countThread, ISerializerFactory serializedFactory)
    {
        checkArgument(propsKafka != null);
        this.propsKafka = propsKafka;
        this.topicName = topicName;
        this.serializedFactory = serializedFactory;
        DefaultThreadFactory threadFactory = new DefaultThreadFactory("KafkaReader");
        this.serviceKafka = new ScheduledThreadPoolExecutor(countThread, threadFactory);
        this.maxCountThread = countThread;
    }

    @Override
    public synchronized void commit()
    {
        for (Entry<Shard, KafkaShardWorker> entry : workers.entrySet())
        {
            entry.getValue().commit();
        }
    }

    @Override
    public synchronized void begin(List<InetSocketAddress> brokers, Shard shard,
            IKafkaReaderBufferController kafkaReaderBufferController, IRecoveryService recoveryService)
            throws KafkaException
    {
        if (processes.size() < maxCountThread)
        {
            if (processes.get(shard) == null)
            {
                KafkaShardWorker kafkaWorker = new KafkaShardWorker(propsKafka, brokers, topicName, shard.getShardNumber(),
                        serializedFactory, kafkaReaderBufferController, recoveryService);
                try
                {
                    ScheduledFuture<?> future =
                            serviceKafka.scheduleWithFixedDelay(kafkaWorker, TIMEOUT, TIMEOUT, TimeUnit.SECONDS);
                    workers.put(shard, kafkaWorker);
                    processes.put(shard, future);
                }
                catch (RejectedExecutionException ex)
                {
                    throw new KafkaException(ex);
                }
            }
        }
    }

    @Override
    public synchronized void end(Shard shard) throws KafkaException
    {
        Future<?> future = processes.get(shard);
        if (future != null)
        {
            boolean calceled = future.cancel(true);
            if (!calceled)
                throw new KafkaException(Messages.failed_cancel_thread());
            processes.remove(shard);
            workers.remove(shard);
        }
    }

    @Override
    public void initialize()
    {
    }

    @Override
    public void release()
    {
        for (Entry<Shard, ScheduledFuture<?>> entry : processes.entrySet())
            entry.getValue().cancel(true);
        for (Entry<Shard, KafkaShardWorker> entry : workers.entrySet())
            entry.getValue().close();
        processes.clear();
        workers.clear();
        serviceKafka.shutdown();
    }

    @Override
    public int maxCountShards()
    {
        return maxCountThread;
    }

    @Override
    public synchronized boolean isWork(Shard shard)
    {
        return processes.get(shard) == null ? false : true;
    }

    @Override
    public void setCommitOffset(Shard shard)
    {
        KafkaShardWorker worker = workers.get(shard);
        if (worker != null)
            worker.setCommitOffset();
    }

    @Override
    public boolean isCommitted(Shard shard)
    {
        KafkaShardWorker worker = workers.get(shard);
        if (worker != null)
            return worker.isCommitted();
        return false;
    }

    @Localizable
    interface IMessagesList
    {
        IMessagesList Messages = LocalizableFactory.create(IMessagesList.class);

        @DefaultString("Cannot stop event reader thread.")
        @Context("В случае ошибки остановки потока по чтению событий из временного хранилища.")
        @Tags({"logs"})
        String failed_cancel_thread();
    }
}
