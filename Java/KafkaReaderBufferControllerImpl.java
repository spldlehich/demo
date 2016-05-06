---
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
---
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Реализация контроллера за очередью аггрегатора и потоком читателя из временного хранилища.
 *
 * <p>
 * <b>Synchronization</b>
 * <p>
 * Экземпляр данного класса является потокобезопасным.
 *
 * @see IKafkaReaderBufferController
 */
public class KafkaReaderBufferControllerImpl implements IKafkaReaderBufferController
{
    private final IAggregator aggregator;
    private final IKafkaReader kafkaReader;
    private final Deque<ClusterEvents> buffer = new ArrayDeque<>();
    private final IRecoveryService recoveryService;
    private final int fetchMaxSize;

    /**
     * Конструктор.
     * 
     * @param aggregator агрегатор
     * @param kafkaReader читатель из временного хранилища
     * @param recoveryService сервис восстановления после сбоя
     * @param fetchMaxSize максимальный разбер батча на выборку
     */
    public KafkaReaderBufferControllerImpl(IAggregator aggregator, IKafkaReader kafkaReader,
            IRecoveryService recoveryService, int fetchMaxSize)
    {
        checkArgument(aggregator != null);
        checkArgument(kafkaReader != null);
        this.aggregator = aggregator;
        this.kafkaReader = kafkaReader;
        this.recoveryService = recoveryService;
        this.fetchMaxSize = fetchMaxSize;
    }

    @Override
    public synchronized boolean addEvents(ClusterEvents events)
    {
        checkArgument(events != null);
        if (buffer.size() >= fetchMaxSize)
        {
            return false;
        }
        buffer.push(events);
        return true;
    }

    @Override
    public synchronized boolean isFull()
    {
        return buffer.size() >= fetchMaxSize ? true : false;
    }

    @Override
    public synchronized boolean aggregate(Shard shard)
    {
        if (!kafkaReader.isCommitted(shard))
            return false;
        MetaClusterEvent metaInfo = recoveryService.getMetainfo();
        if (metaInfo == null && !buffer.isEmpty())
            throw new IllegalStateException("Metainfo is null, but buffer event not empty!");
        try
        {
            aggregator.addEvent(buffer, metaInfo == null ?  Optional.empty() : Optional.of(metaInfo));
        }
        catch (LoadException e)
        {
            throw new BufferException(e);
        }
        buffer.clear();
        // В данном блоке процесс не может читать события,
        // поэтому задать оффсет нужно тут.
        kafkaReader.setCommitOffset(shard);
        return true;
    }

    @Override
    public int fetchMaxSize()
    {
        return fetchMaxSize;
    }
}
