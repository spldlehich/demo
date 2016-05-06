---
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * <code>LogEntriesFilter</code> класс предназначенный для хранения условий фильтрации выборки из хранилища. Хранит в
 * себе необходимые данные для фильтрации.
 * <p>
 * Вариант использования:
 *
 * <pre>
 * LogEntriesFilter filter = new LogEntriesFilter.Builder().build();
 * </pre>
 * 
 * или
 * 
 * <pre>
 * LogEntriesFilter filter = new LogEntriesFilter();
 * </pre>
 * <p>
 * <b>Synchronization</b>
 * <p>
 * Экземпляр данного класса не является потокобезопасным.
 *
 */
public class LogEntriesFilter
{
    private Date dateFrom;
    private Date dateTo;
    private Set<UuidNamePair> users;
    private Set<String> hostsName;
    private Set<String> appIds;
    private Set<String> severities;
    private Set<String> events;
    private Set<String> excludeEvents;
    private String comment;
    private Set<UuidNamePair> metadata;
    private String data;
    private Long dataType;
    //    DBRecordRef dataRecordRef;
    //    EDBRecordRef dataRecordRefEDB;
    private String dataPresentation;
    private Set<Long> seanceNumbs;
    private Set<String> transactionId;
    private Set<String> transactionStatus;
    private Set<String> serverHostsName;
    private Set<Long> primaryPort;
    private Set<Long> secondaryPort;
    private Set<Splitter> splitters;
    private Set<Splitter> unusedSplitters;

    private LogEntriesFilter(Builder builder)
    {
        this.dateFrom = builder.dateFrom;
        this.dateTo = builder.dateTo;
        this.users = builder.users;
        this.hostsName = builder.hostsName;
        this.appIds = builder.appIdCodes;
        this.severities = builder.severities;
        this.events = builder.events;
        this.excludeEvents = builder.excludeEvents;
        this.comment = builder.comment;
        this.metadata = builder.metadatas;
        this.data = builder.data;
        this.dataType = builder.dataType;
        this.dataPresentation = builder.dataPresentation;
        this.seanceNumbs = builder.seanceNumbs;
        this.transactionId = builder.transactionId;
        this.transactionStatus = builder.transactionStatus;
        this.serverHostsName = builder.serverHostsName;
        this.primaryPort = builder.primaryPort;
        this.secondaryPort = builder.secondaryPort;
        this.splitters = builder.splitters;
        this.unusedSplitters = builder.unusedSplitters;
    }

    /**
     * Метод, который указывает, что условия для выборки пустые. Временной диапазон не учитывается, так как не является
     * прямым условием для выборки. Используется для облегчения выборки/удаления событий.
     * 
     * @return флаг о заданных условиях выборки
     */
    public boolean isNullDynamicFields()
    {
        return (users.isEmpty()
                && hostsName.isEmpty()
                && appIds.isEmpty()
                && severities.isEmpty()
                && events.isEmpty()
                && excludeEvents.isEmpty()
                && comment == null
                && metadata.isEmpty()
                && data == null
                && dataType == null
                && dataPresentation == null
                && seanceNumbs.isEmpty()
                && transactionId.isEmpty()
                && transactionStatus.isEmpty()
                && serverHostsName.isEmpty()
                && primaryPort.isEmpty()
                && secondaryPort.isEmpty()
                && splitters.isEmpty()
                && unusedSplitters.isEmpty());
    }

    /**
     * Возвращает множество разделителей
     * 
     * @return разделители
     */
    public Set<Splitter> getSplitters()
    {
        return Collections.unmodifiableSet(splitters);
    }

    /**
     * Возвращает множество имен разделителей, у которых нет uuid
     * 
     * @return разделители
     */
    public Set<String> getEmptyUuidSplitters()
    {
        Set<String> set = new HashSet<>();
        for(Splitter split: splitters)
        {
            if (split.hasName() && !split.hasUuid())
                set.add(split.getName());
        }
        return set;
    }

    /**
     * Возвращает множество неиспользуемых разделителей
     * 
     * @return разделители
     */
    public Set<Splitter> getUnusedSplitters()
    {
        return Collections.unmodifiableSet(unusedSplitters);
    }

    /**
     * Возвращает множество имен неиспользуемых разделителей, у которых нет uuid
     * 
     * @return разделители
     */
    public Set<String> getEmptyUuidUnusedSplitters()
    {
        Set<String> set = new HashSet<>();
        for(Splitter split: unusedSplitters)
        {
            if (split.hasName() && !split.hasUuid())
                set.add(split.getName());
        }
        return set;
    }

    /**
     * Возвращает тип данных для выборки
     * 
     * @return тип
     */
    public Long getDataType()
    {
        return dataType;
    }

    /**
     * Возвращает множество идентификаторов транзакций
     * 
     * @return идентификаторы
     */
    public Set<String> getTransactionId()
    {
        return transactionId;
    }

    /**
     * Возвращает множество идентификаторов метаданных
     * 
     * @return идентификаторы
     */
    public Set<UuidNamePair> getMetadata()
    {
        return Collections.unmodifiableSet(metadata);
    }

    /**
     * Возвращает множество идентификаторов метаданных без uuid
     * 
     * @return идентификаторы
     */
    public Set<String> getEmptyUuidMetadatas()
    {
        Set<String> set = new HashSet<>();
        for(UuidNamePair pair: metadata)
        {
            if (isNullOrEmpty(pair.getUuid()) && !isNullOrEmpty(pair.getName()))
                set.add(pair.getName());
        }
        return set;
    }

    /**
     * Возвращает множество хостов
     * 
     * @return хосты
     */
    public Set<String> getHostsName()
    {
        return Collections.unmodifiableSet(hostsName);
    }

    /**
     * Возвращает множество событий, для исключения из списка
     * 
     * @return множество событий
     */
    public Set<String> getExcludeEvents()
    {
        return Collections.unmodifiableSet(excludeEvents);
    }

    /**
     * Возвращает данные
     * 
     * @return данные
     */
    public String getData()
    {
        return data;
    }

    /**
     * Возвращает множество первичных портов
     * 
     * @return порты
     */
    public Set<Long> getPrimaryPort()
    {
        return Collections.unmodifiableSet(primaryPort);
    }

    /**
     * Возвращает множество вторичных портов
     * 
     * @return порты
     */
    public Set<Long> getSecondaryPort()
    {
        return Collections.unmodifiableSet(secondaryPort);
    }

    /**
     * Возвращает множество наименований событий
     * 
     * @return наименования
     */
    public Set<String> getEvents()
    {
        return Collections.unmodifiableSet(events);
    }

    /**
     * Возвращает время начала поступления событий(включая границу)
     *
     * @return время
     */
    public Date getDateFrom()
    {
        return dateFrom;
    }

    /**
     * Возвращает время завершения поступления событий (исключая границу)
     * 
     * @return время
     */
    public Date getDateTo()
    {
        return dateTo;
    }

    /**
     * Возвращает множество идентификаторов пользователей
     * 
     * @return идентификаторы
     */
    public Set<UuidNamePair> getUsers()
    {
        return Collections.unmodifiableSet(users);
    }

    /**
     * Возвращает множество идентификаторов пользователей без uuid
     * 
     * @return идентификаторы
     */
    public Set<String> getEmptyUuidUsers()
    {
        Set<String> set = new HashSet<>();
        for(UuidNamePair pair: users)
        {
            if (pair.hasName() && !pair.hasUuid())
                set.add(pair.getName());
        }
        return set;
    }

    /**
     * Возвращает множество идентификаторов приложений
     * 
     * @return
     */
    public Set<String> getAppIds()
    {
        return Collections.unmodifiableSet(appIds);
    }

    /**
     * Возвращаетуровень события
     * 
     * @return уровень
     */
    public Set<String> getSeverities()
    {
        return Collections.unmodifiableSet(severities);
    }

    /**
     * Возвращает комментарий
     * 
     * @return комментарий
     */
    public String getComment()
    {
        return comment;
    }

    /**
     * Возвращает данные в строковом представлении
     *
     * @return данные
     */
    public String getDataPresentation()
    {
        return dataPresentation;
    }

    /**
     * Возвращает номер сеанса
     * 
     * @return номер
     */
    public Set<Long> getSeanceNumbs()
    {
        return Collections.unmodifiableSet(seanceNumbs);
    }

    /**
     * Получает идентификатор транзакции
     * 
     * @return идентификатор
     */
    public Set<String> getTransactionStatus()
    {
        return Collections.unmodifiableSet(transactionStatus);
    }

    /**
     * Получает множество имен хостов
     * 
     * @return хосты
     */
    public Set<String> getServerHostsName()
    {
        return Collections.unmodifiableSet(serverHostsName);
    }

    /**
     * <code>Builder</code> билдер
     *
     * <p>
     * Вариант использования:
     *
     * <pre>
     * LogEntriesFilter filter = new LogEntriesFilter.Builder().build();
     * </pre>
     *
     * <p>
     * <b>Synchronization</b>
     * <p>
     * Экземпляр данного класса является потокобезопасным.
     *
     * @author Garanin_A
     */
    public static class Builder
    {
        private Date dateFrom;
        private Date dateTo;
        private Set<UuidNamePair> users = new HashSet<>();
        private Set<String> hostsName = new HashSet<>();
        private Set<String> appIdCodes = new HashSet<>();
        private Set<String> severities = new HashSet<>();
        private Set<String> events = new HashSet<>();
        private Set<String> excludeEvents = new HashSet<>();
        private String comment;
        private Set<UuidNamePair> metadatas = new HashSet<>();
        private String data;
        private Long dataType;
        private String dataPresentation;
        private Set<Long> seanceNumbs = new HashSet<>();
        private Set<String> transactionId = new HashSet<>();
        private Set<String> transactionStatus = new HashSet<>();
        private Set<String> serverHostsName = new HashSet<>();
        private Set<Long> primaryPort = new HashSet<>();
        private Set<Long> secondaryPort = new HashSet<>();
        private Set<Splitter> splitters = new HashSet<>();
        private Set<Splitter> unusedSplitters = new HashSet<>();

        /**
         * Задает интервал
         * 
         * @param dateFrom время начала поиска
         * @return билдер
         */
        public Builder dateFrom(Date dateFrom)
        {
            this.dateFrom = dateFrom;
            return this;
        }

        /**
         * Задает интервал
         * 
         * @param dateTo время окончания поиска
         * @return билдер
         */
        public Builder dateTo(Date dateTo)
        {
            this.dateTo = dateTo;
            return this;
        }

        /**
         * Задает идентификаторы пользователей
         * 
         * @param users идентификаторы
         * @return билдер
         */
        public Builder users(Set<UuidNamePair> users)
        {
            this.users = users;
            return this;
        }

        /**
         * Задает хосты
         * 
         * @param hostsName хосты
         * @return билдер
         */
        public Builder hostsName(Set<String> hostsName)
        {
            this.hostsName = hostsName;
            return this;
        }

        /**
         * Задает идентификаторы приложений
         * 
         * @param appIdCodes идентификаторы
         * @return билдер
         */
        public Builder appIdCodes(Set<String> appIdCodes)
        {
            this.appIdCodes = appIdCodes;
            return this;
        }

        /**
         * задает важность событий
         * 
         * @param severities важность
         * @return билдер
         */
        public Builder severities(Set<String> severities)
        {
            this.severities = severities;
            return this;
        }

        /**
         * Задает события
         * 
         * @param events события
         * @return билдер
         */
        public Builder events(Set<String> events)
        {
            this.events = events;
            return this;
        }

        /**
         * Задает события, которые не включаются в выборку
         * 
         * @param excludeEvents события
         * @return билдер
         */
        public Builder excludeEvents(Set<String> excludeEvents)
        {
            this.excludeEvents = excludeEvents;
            return this;
        }

        /**
         * Задает комментарии
         * 
         * @param comment комментарий
         * @return билдер
         */
        public Builder comment(String comment)
        {
            this.comment = comment;
            return this;
        }

        /**
         * Задает идентификаторы метаданных
         * 
         * @param metadatas идентификаторы
         * @return билдер
         */
        public Builder metadatas(Set<UuidNamePair> metadatas)
        {
            this.metadatas = metadatas;
            return this;
        }

        /**
         * Задает данные
         * 
         * @param data данные
         * @return билдер
         */
        public Builder data(String data)
        {
            this.data = data;
            return this;
        }

        /**
         * Задает тип данных
         * 
         * @param dataType тип
         * @return билдер
         */
        public Builder dataType(Long dataType)
        {
            this.dataType = dataType;
            return this;
        }

        /**
         * Задает данные в строковом представлении
         * 
         * @param dataPresentation данные
         * @return билдер
         */
        public Builder dataPresentation(String dataPresentation)
        {
            this.dataPresentation = dataPresentation;
            return this;
        }

        /**
         * Задает номера сеансов
         * 
         * @param seanceNumbs номера сеансов
         * @return билдер
         */
        public Builder seanceNumbs(Set<Long> seanceNumbs)
        {
            this.seanceNumbs = seanceNumbs;
            return this;
        }

        /**
         * Задает идентификаторы транзакций
         * 
         * @param transactionId идентификаторы
         * @return билдер
         */
        public Builder transactionId(Set<String> transactionId)
        {
            this.transactionId = transactionId;
            return this;
        }

        /**
         * Задает статусы транзакций
         * 
         * @param transactionStatus статусы транзакций
         * @return билдер
         */
        public Builder transactionStatus(Set<String> transactionStatus)
        {
            this.transactionStatus = transactionStatus;
            return this;
        }

        /**
         * Задает множество серверных хостов
         * 
         * @param serverHostsName хосты
         * @return билдер
         */
        public Builder serverHostsName(Set<String> serverHostsName)
        {
            this.serverHostsName = serverHostsName;
            return this;
        }

        /**
         * Задает основные порты
         * 
         * @param primaryPort порты
         * @return билдер
         */
        public Builder primaryPort(Set<Long> primaryPort)
        {
            this.primaryPort = primaryPort;
            return this;
        }

        /**
         * Задает дополнительные порты
         * 
         * @param secondaryPort порты
         * @return билдер
         */
        public Builder secondaryPort(Set<Long> secondaryPort)
        {
            this.secondaryPort = secondaryPort;
            return this;
        }

        /**
         * Задает разделители
         * 
         * @param splitters разделители
         * @return билдер
         */
        public Builder splitters(Set<Splitter> splitters)
        {
            this.splitters = splitters;
            return this;
        }

        /**
         * Задает неиспользуемые разделители
         * 
         * @param splitters разделители
         * @return билдер
         */
        public Builder unusedSplitters(Set<Splitter> splitters)
        {
            this.unusedSplitters = splitters;
            return this;
        }

        /**
         * Задает фильтр
         * 
         * @param logEntriesFilter фильтр
         * @return билдер
         */
        public Builder logEntriesFilter(LogEntriesFilter logEntriesFilter)
        {
            this.dateFrom = logEntriesFilter.dateFrom;
            this.dateTo = logEntriesFilter.dateTo;
            this.users = logEntriesFilter.users;
            this.hostsName = logEntriesFilter.hostsName;
            this.appIdCodes = logEntriesFilter.appIds;
            this.severities = logEntriesFilter.severities;
            this.events = logEntriesFilter.events;
            this.excludeEvents = logEntriesFilter.excludeEvents;
            this.comment = logEntriesFilter.comment;
            this.metadatas = logEntriesFilter.metadata;
            this.data = logEntriesFilter.data;
            this.dataType = logEntriesFilter.dataType;
            this.dataPresentation = logEntriesFilter.dataPresentation;
            this.seanceNumbs = logEntriesFilter.seanceNumbs;
            this.transactionId = logEntriesFilter.transactionId;
            this.transactionStatus = logEntriesFilter.transactionStatus;
            this.serverHostsName = logEntriesFilter.serverHostsName;
            this.primaryPort = logEntriesFilter.primaryPort;
            this.secondaryPort = logEntriesFilter.secondaryPort;
            this.splitters = logEntriesFilter.splitters;
            this.unusedSplitters = logEntriesFilter.unusedSplitters;
            return this;
        }

        /**
         * Строит объект фильтра
         * 
         * @return фильтр
         */
        public LogEntriesFilter build()
        {
            return new LogEntriesFilter(this);
        }
    }
}
