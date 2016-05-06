---
import javax.annotation.Nullable;
---
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Ребалансировка шардов. Используется следующий подход:
 * <ul>
 * <li>загрузка информации о шардах и компонентах
 * <li>пробуем перебалансировать шарды так, чтобы как можно меньше изменений было в листах, т.к. не следует
 * перезапускать процессы чтения.
 * <li>нормализация(убираем те, что уже используются, если они в другом читателе.)
 * </ul>
 */
public class MaintenanceRebalancer
{
    private List<ComponentInfo> consumers = new ArrayList<>();
    private List<ComponentInfo> receivers = new ArrayList<>();
    private List<AssignedShard> reservedShards = new ArrayList<>();
    private List<AssignedShard> usedShards = new ArrayList<>();
    private List<AssignedShard> cleanUsed = new ArrayList<>();
    /**
     * Добавляем информацию о читателях.
     * 
     * @param consumers читатели
     */
    public void addConsumers(List<ComponentInfo> consumers)
    {
        this.consumers.addAll(consumers);
    }

    /**
     * Возвращает те разделы, которые надо удалить из используемых.
     * @return разделы
     */
    public List<AssignedShard> cleanUsed()
    {
        return cleanUsed;
    }

    /**
     * Добавляем информацию о приемниках.
     * 
     * @param receivers приемники
     */
    public void addReceivers(List<ComponentInfo> receivers)
    {
        this.receivers.addAll(receivers);
    }

    /**
     * Добавляем информацию о зарезервированных шардах.
     *
     * @param reservedShards шарды
     */
    public void addReservedShards(List<AssignedShard> reservedShards)
    {
        this.reservedShards.addAll(reservedShards);
    }

    /**
     * Добавляем информацию о используемых шардах.
     *
     * @param usedShards шарды
     */
    public void addUsedShards(List<AssignedShard> usedShards)
    {
        this.usedShards.addAll(usedShards);
    }

    /**
     * Балансировка.
     * 
     * @return списов с информацией о шардах и привязке к приемнику и читателю.
     */
    public List<AssignedShard> balance()
    {
        if (consumers.isEmpty())
            return Collections.emptyList();

        //  Конструируем список всех шардов, необходимых для обработки.
        Set<Shard> shards = new HashSet<>();
        for (ComponentInfo receiver : receivers)
        {
            int countShard = receiver.getShardsCount();
            for (int i = 0; i < countShard; ++i)
            {
                shards.add(new Shard(receiver, i));
            }
        }

        // Проверяем актуальность
        reservedShards = cleanNotAvail(reservedShards);
        List<AssignedShard> availUsedShards = cleanNotAvail(usedShards);
        List<AssignedShard> removedUsedShards = new ArrayList<>(usedShards);
        removedUsedShards.removeAll(availUsedShards);
        cleanUsed = removedUsedShards;

        //  читатель:стек<информация о шарде>.
        Map<ComponentInfo, Deque<Shard>> consumersDeque = getDequeShards(shards);

        // распределяем шарды согласно нагрузке на читателей
        calc(consumersDeque, shards);

        // распределяем те шарды, которые не смогли распределиться по читателям из-за превышения нагрузки
        calcOther(consumersDeque, shards);

        // Нормализуем
        List<AssignedShard> results = normalize(consumersDeque);

        // Удаляем те, что используются другими нодами(читателя)
        return removeUsedOnOtherNode(results);
    }

    private List<AssignedShard> cleanNotAvail(List<AssignedShard> shards)
    {
        Set<UUID> consumerNameSet = new HashSet<>();
        for (ComponentInfo consumer : consumers)
        {
            consumerNameSet.add(consumer.getId());
        }        
        Set<UUID> receiverNameSet = new HashSet<>();
        for (ComponentInfo receiver : receivers)
        {
            receiverNameSet.add(receiver.getId());
        }
        List<AssignedShard> actual = new ArrayList<>(shards);
        actual.removeIf(new java.util.function.Predicate<AssignedShard>()
        {
            @Override
            public boolean test(AssignedShard t)
            {
                return !receiverNameSet.contains(t.getShard().getReceiver().getId())
                        || !consumerNameSet.contains(t.getConsumer().getId());
            }
        });
        return actual;
    }

    private List<AssignedShard> normalize(Map<ComponentInfo, Deque<Shard>> consumersDeque)
    {
        List<AssignedShard> shards = new ArrayList<>();
        for (Entry<ComponentInfo, Deque<Shard>> shardEntry : consumersDeque.entrySet())
        {
            while (shardEntry.getValue() != null && !shardEntry.getValue().isEmpty())
            {
                Shard info = shardEntry.getValue().pop();
                shards.add(new AssignedShard(info, shardEntry.getKey()));
            }
        }
        return shards;
    }

    private List<AssignedShard> removeUsedOnOtherNode(List<AssignedShard> shards)
    {
        Map<ComponentInfo, Set<AssignedShard>> used = new HashMap<>();
        for (AssignedShard us : usedShards)
        {
            Set<AssignedShard> set = used.computeIfAbsent(us.getConsumer(), v -> new HashSet<>());
            set.add(us);
        }

        Iterator<AssignedShard> it = shards.iterator();
        while (it.hasNext())
        {
            AssignedShard info = it.next();
            Iterable<AssignedShard> currentRemove = Iterables.filter(usedShards, new Predicate<AssignedShard>()
            {
                @Override
                public boolean apply(@Nullable AssignedShard input)
                {
                    return input.getShard().equals(info.getShard())
                            && !input.getConsumer().equals(info.getConsumer());
                }
            });
            if (currentRemove.iterator().hasNext())
                it.remove();
        }
        return shards;
    }

    private Map<ComponentInfo, Deque<Shard>> getDequeShards(Set<Shard> shards)
    {
        // Подготовим для балансировки, создадим мапу читатель:стек<информация о шарде>.
        // Именно стек - чтоб меньше затрагивать те шарды, которые уже обрабатываются. Новые - наверху
        Map<ComponentInfo, Deque<Shard>> consumersDeque = new HashMap<>();
        for (AssignedShard shard : reservedShards)
        {
            ComponentInfo consumer = shard.getConsumer();
            Deque<Shard> consumerShards = consumersDeque.computeIfAbsent(consumer, v -> new ArrayDeque<>());
            consumerShards.push(shard.getShard());
            // Что останется - нужно распределять
            shards.remove(shard.getShard());
        }
        return consumersDeque;
    }

    private void calc(Map<ComponentInfo, Deque<Shard>> consumersDeque, Set<Shard> shards)
    {
        // "срезаем" избыточные
        float avLoad = avLoad(consumersDeque, shards);
        for (ComponentInfo consumer : consumers)
        {
            Deque<Shard> th = consumersDeque.get(consumer);
            int sizeShards = 0;
            if (th != null)
                sizeShards = th.size();
            float currentLoad = (float)sizeShards / (float)consumer.getShardsCount();
            if (th == null)
                continue;
            while (currentLoad > avLoad)
            {
                shards.add(th.pop());
                sizeShards = th.size();
                currentLoad = (float)sizeShards / (float)consumer.getShardsCount();
            }
        }

        // Наращиваем
        for (ComponentInfo consumer : consumers)
        {
            Deque<Shard> th = consumersDeque.computeIfAbsent(consumer, v -> new ArrayDeque<>());
            int sizeShards = th.size();
            float currentLoad = (float)sizeShards / (float)consumer.getShardsCount();
            Iterator<Shard> it = shards.iterator();
            while (currentLoad < avLoad)
            {
                if (it.hasNext())
                {
                    Shard shardInfo = it.next();
                    th.push(shardInfo);
                    it.remove();
                    sizeShards = th.size();
                    currentLoad = (float)sizeShards / (float)consumer.getShardsCount();
                }
                else
                    break;
            }
        }
    }

    private void calcOther(Map<ComponentInfo, Deque<Shard>> consumersDeque, Set<Shard> shards)
    {
        // читатель : макс. количество шардов - для распределения оставшихся
        Map<ComponentInfo, Integer> mapConsumers = new HashMap<>();
        for (ComponentInfo consumer : consumers)
        {
            mapConsumers.put(consumer, consumer.getShardsCount());
        }

        // Распределяем оставшиеся.
        Deque<Shard> excludedShards = new ArrayDeque<>();
        excludedShards.addAll(shards);
        for (int i = 0; i < consumersDeque.size(); ++i)
        {
            for (Entry<ComponentInfo, Deque<Shard>> shardEntry : consumersDeque.entrySet())
            {
                Integer maxShard = mapConsumers.get(shardEntry.getKey());
                if (maxShard != null && ((shardEntry.getValue().size() + 1) <= maxShard) && !excludedShards.isEmpty())
                {
                    shardEntry.getValue().push(excludedShards.pop());
                }
                if (excludedShards.isEmpty())
                    break;
            }
            if (excludedShards.isEmpty())
                break;
        }
    }

    private float avLoad(Map<ComponentInfo, Deque<Shard>> consumersDeque, Set<Shard> shards)
    {
        int countAllConsumerShards = 0;
        // читатель : макс. количество шардов
        for (ComponentInfo consumer : consumers)
        {
            countAllConsumerShards += consumer.getShardsCount();
        }
        int countAllReceiversShards = 0;
        // приемник : макс. количество шардов
        for (ComponentInfo receiver : receivers)
        {
            countAllReceiversShards += receiver.getShardsCount();
        }
        float result = (float)countAllReceiversShards / (float)countAllConsumerShards;
        return result;
    }
}
