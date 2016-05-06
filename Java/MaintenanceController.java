---
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
---
import com.google.inject.Inject;

/**
 * Класс-контроллер отвечает за фиксирование изменения в хранилище и сдвиг офсетов Kafka. Фиксация происходит через
 * определенный интервал времени(commit.timeout). При успешном выполнении происходит сдвиг офсетов. На время фиксации
 * изменений, потоки для чтения сообщений приостанавливаются для избежания неверного сдвига офсетов.
 *
 * <p>
 * <b>Synchronization</b>
 * <p>
 * Экземпляр данного класса является потокобезопасным.
 */
public class MaintenanceController implements IController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceController.class);
    private final Map<Shard, IKafkaReaderBufferController> buffers = new HashMap<>();
    private final Map<Shard, IRecoveryService> recoveryServices = new HashMap<>();
    @Inject
    private IKafkaReader kafkaReader;
    @Inject
    private IAggregator aggregator;
    @Inject
    private MaintenanceConfig consumerConfig;

    @Override
    public void run()
    {
        work();
    }

    @Override
    public synchronized void startThread(List<InetSocketAddress> brokers, Shard shard) throws ControllerException
    {
        IRecoveryService mongoRecoveryService = recoveryServices.computeIfAbsent(shard,
                v -> new MongoRecoveryService(consumerConfig.getStorages()));
        IKafkaReaderBufferController buffer = buffers.computeIfAbsent(shard, v -> new KafkaReaderBufferControllerImpl(
                aggregator, kafkaReader, mongoRecoveryService, consumerConfig.getMaxFetchSize()));
        try
        {
            kafkaReader.begin(brokers, shard, buffer, mongoRecoveryService);
        }
        catch (KafkaException e)
        {
            throw new ControllerException(e);
        }
    }

    @Override
    public synchronized void stopThread(Shard shard) throws ControllerException
    {
        try
        {
            kafkaReader.end(shard);
        }
        catch (KafkaException e)
        {
            throw new ControllerException(e);
        }
        buffers.remove(shard);
        recoveryServices.remove(shard);
    }

    @Override
    public int maxCountShards()
    {
        return kafkaReader.maxCountShards();
    }

    @Override
    public boolean isWork(Shard shard)
    {
        return kafkaReader.isWork(shard);
    }

    /**
     * Можно наследоваться и реализовать свою версию контроллера по организации загрузки событий в хранилище.
     */
    protected void work()
    {
        try
        {
            try
            {
                mergeEvents();
                while (aggregator.hasData() && !Thread.currentThread().isInterrupted())
                    aggregator.push();
            }
            catch (LoadException | BufferException e)
            {
                LOGGER.warn(e.getMessage());
                LOGGER.trace("Load message into storage exception.", e);
            }
            kafkaReader.commit();
        }
        // На случай, если что-то совсем не так. Не следует выпускать отсюда исключение.
        // Т.е. не нужно "ломать" поток, нужно продолжать попытки, но при этом не коммитить в Кафку.
        catch (Exception e)
        {
            LOGGER.warn(Messages.unexpected_exception(e.getMessage()));
            LOGGER.trace("Unexpected exception.", e);
        }
    }

    private synchronized void mergeEvents() throws BufferException
    {
        for (Entry<Shard, IKafkaReaderBufferController> entry : buffers.entrySet())
        {
            IKafkaReaderBufferController buffer = entry.getValue();
            buffer.aggregate(entry.getKey());
        }
    }

    @Localizable
    interface IMessagesList
    {
        IMessagesList Messages = LocalizableFactory.create(IMessagesList.class);

        @DefaultString("The events loading process has stopped because of the unexpected error: {0}")
        @Context("Если возникла ошибка, которую не удается распознать.")
        @Tags({"logs"})
        String unexpected_exception(String value);
    }
}
