---

import java.util.List;

/**
 * Клиентское API по взаимодействию с keeper-сервером.
 * <p>
 * <i>Keeper-сервером</i> называется отдельный процесс в ОС, отвечающий
 * за координацию процессов кластера, контроль доступности процессов
 * кластера, обмен произвольной информацией между процессами (например
 * статистические данные), хранение основной конфигурационной информации для
 * процессов кластера (примечание: эта часть со временем может быть выделена в
 * сервис конфигурации).
 *
 * <p>
 * Программная модель и модель данных этого сервиса заимствуются из
 * Apache ZooKeeper, однако некоторые методы изменены или не поддерживаются
 * намеренно. Детали описаны в документации на этот интерфейс и конкретные методы.
 * Изменения вносились по следующим причинам:
 * <ol>
 *     <li>функциональность не используется</li>
 *     <li>предполагается, что ограничив поддерживаемое API,
 * <i>теоретически</i> можно заменить ZooKeeper
 * на собственную реализацию сервера, <i>пригодную только для небольшого
 * кластера (единицы рабочих процессов) и имеющую более низкую
 * производительность, однако стабильно работающую в условиях ограниченных
 * ресурсов</i>
 * </li></ol>
 *
 * <p>
 * В один момент времени в системе может быть только одна реализация
 * данного интерфейса. Подразумевается что данный интерфейс будет доступен
 * в OSGI Service Registry. Кроме того, при остановке этого бандла целесообразно
 * останавливать вообще все приложение, т.к. через этот интерфейс идет обмен
 * жизненно важной информацией о состоянии процессов.
 *
 * <p><b>Synchronization</b>
 * <p>Экземпляр данного класса/интерфейса является потокобезопасным.
 *
 * <p><b>Терминология и дополнения</b>
 * <p>
 * <ul>
 *     <li><b>узел</b> - ключ в древовидной структуре данных, характеризуется
 * <b>полным путем</b> и <b>типом</b>. Имеет 1 <b>родительский узел</b>
 * (кроме <b> корневого узла</b>), и произвольное количество <b>дочерних
 * узлов</b>.</li>
 *     <li><b>полный путь</b> - строка, разделенная слэшами и начинающаяся
 * со слэша (символ {@code /}). Полный список ограничений доступен
 * <a href="http://zookeeper.apache.org/doc/r3.4.5/zookeeperProgrammers.html#ch_zkDataModel">в
 * документации</a></li>
 *     <li><b>тип узла - постоянный или эфемерный</b>. Узел является постоянным,
 * если явно не указано, что он эфемерный. Постоянный узел сохраняется между
 * различными сеансами связи keeper-сервера и клиента, эфемерный пропадает
 * если сеанс завершается.</li>
 *     <li><b>последовательный узел</b> - к пути узла добавляется уникальный
 * номер, подробнее см. <a href="http://zookeeper.apache.org/doc/r3.4.5/zookeeperProgrammers.html#Sequence+Nodes+--+Unique+Naming">Sequence
 * Nodes</a></li>
 *     <li><b>сеанс связи</b>, описанный выше фактически есть Session в
 * терминах ZooKeeper. Важно, что реализация данного интферфейса
 * скрывает эти детали от конечных потребителей. Требуется чтобы
 * реализация была выполнена с поддержанием постоянного соединения
 * к keeper-серверу и в случае его разрыва инициировалась операция
 * по остановке процесса.</li>
 * </ul>
 *
 * @see KeeperLayout
 *
 * @author Sergey Guriev
 */
public interface IClusterKeeper
{
    /**
     * Ряд операций принимает версию узла для проверки на строгое соответствие
     * указанной версии узла и актуальной версии на сервере - используя
     * эту константу следует использовать для пропуска описанной проверки.
     */
    int ANY_VERSION = -1;

    /**
     * Возвращает имя кластера с которым работает данный экземпляр сервиса.
     *
     * <p>Один экземпляр сервиса может работать только с данными одного кластера.
     * Имя кластера как правило определяется из конфигурации.
     *
     * @return не {@code null} и не пустая строка.
     */
    String getClusterName();

    /**
     * Подключение обработчика состояния соединения.
     *
     * <p>Один из методов обработчика будет немедленно вызван в зависимости
     * от текущего состояния соединения.
     *
     * @param listener не {@code null}.
     *
     * @throws java.lang.IllegalStateException если данный обработчик уже был добавлен.
     */
    void addListener(IConnectionListener listener);

    /**
     * Удаляет обработчика состояния соединения.
     *
     * <p>
     * Удаление происходит асинхронно - удаленный обработчик с небольшой долей
     * вероятности может получить уведомление об изменении статуса подключения после удаления.
     *
     * @param listener не {@code null}
     *
     * @throws java.lang.IllegalStateException если данный обработчик не был
     *      ранее зарегистрирован с помощью метода {@link #addListener(IConnectionListener)}.
     */
    void removeListener(IConnectionListener listener);

    /**
     * Создает узел по указанному пути с пустым содержимым.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    void create(String path);

    /**
     * Создает последовательный узел по указанному пути с пустым содержимым.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @return полный путь созданного узла включая определенный сервером номер,
     *      не {@code null} и не пустая строка.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    String createSeq(String path);

    /**
     * Создает эфемерный узел с пустым содержимым по указанному пути.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    void createEphemeral(String path);

    /**
     * Создает эфемерный последовательный узел с пустым содержимым по указанному пути.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @return полный путь созданного узла включая определенный сервером номер, не {@code null} и не пустая строка.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    String createEphemeralSeq(String path);

    /**
     * Создает узел по указанному пути с указанным содержимым.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param data содержимое узла, может быть пустым массивом, {@code null} заменяется на пустой массив.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    void create(String path, byte[] data);

    /**
     * Создает последовательный узел по указанному пути с указанным содержимым.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param data содержимое узла, может быть пустым массивом, {@code null} заменяется на пустой массив.
     *
     * @return полный путь созданного узла включая определенный сервером номер, не {@code null} и не пустая строка.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    String createSeq(String path, byte[] data);

    /**
     * Создает эфемерный узел с указанным содержимым по указанному пути.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param data содержимое узла, может быть пустым массивом, {@code null} заменяется на пустой массив.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    void createEphemeral(String path, byte[] data);

    /**
     * Создает эфемерный последовательный узел с указанным содержимым по указанному пути.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param data содержимое узла, может быть пустым массивом, {@code null} заменяется на пустой массив.
     *
     * @return полный путь созданного узла включая определенный сервером номер, не {@code null} и не пустая строка.
     *
     * @throws NodeExistsException если узел уже существует.
     * @throws NoNodeException если хотя бы один из родительских узлов не существует.
     * @throws EphemeralParentException если родительский узел создаваемого является эфемерным.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    String createEphemeralSeq(String path, byte[] data);

    /**
     * Возвращает данные для узла по указанному пути.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @return не {@code null}.
     *
     * @throws NoNodeException если узел не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    byte[] read(String path);

    /**
     * Возвращает данные и статистику для узла по указанному пути.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param stat контейнер для статистики, не {@code null}, не будет модифицирован если узла не существует.
     *
     * @return не {@code null}.
     *
     * @throws NoNodeException если узел не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    byte[] read(String path, NodeStat stat);

    /**
     * Возвращает данные для узла по указанному пути и устанавливает обработчик событий для узла.
     * <b>Обработчик не будет установлен, если узла не существует.</b>
     * Обработчик будет оповещен в случае изменения данных узла или при удалении узла.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param watcher обработчик событий, не {@code null}.
     *
     * @return не {@code null}.
     *
     * @throws NoNodeException если узел не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    byte[] read(String path, IWatcher watcher);

    /**
     * Возвращает данные и статистику для узла по указанному пути,
     * устанавливает обработчик событий для узла. <b>Обработчик не будет
     * установлен, если узла не существует.</b> Обработчик будет оповещен в
     * случае изменения данных узла или при удалении узла.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param stat контейнер для статистики, не {@code null}, не будет модифицирован если узла не существует.
     * @param watcher обработчик событий, не {@code null}.
     *
     * @return не {@code null}.
     *
     * @throws NoNodeException если узел не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    byte[] read(String path, IWatcher watcher, NodeStat stat);

    /**
     * Возвращает список детей указанного узла.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @return список в произвольном порядке, не {@code null}.
     *
     * @throws NoNodeException если узла не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    List<String> getChildren(String path);

    /**
     * Возвращает статистику и список детей указанного узла.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param stat контейнер для статистики, не {@code null}, не будет модифицирован если узла не существует.
     *
     * @return список в произвольном порядке, не {@code null}.
     *
     * @throws NoNodeException если узла не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    List<String> getChildren(String path, NodeStat stat);

    /**
     * Возвращает список детей указанного узла и устанавливает обработчик
     * событий для узла. <b>Обработчик не будет установлен, если
     * узла не существует.</b> Обработчик будет оповещен в случае
     * удаления узла или в случае создания/удаления дочерних узлов.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param watcher обработчик событий, не {@code null}.
     *
     * @return список в произвольном порядке, не {@code null}.
     *
     * @throws NoNodeException если узла не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    List<String> getChildren(String path, IWatcher watcher);

    /**
     * Возвращает статистику и список детей указанного узла и
     * устанавливает обработчик событий для узла. <b>Обработчик не будет
     * установлен, если узла не существует.</b> Обработчик будет оповещен в
     * случае удаления узла или в случае создания/удаления дочерних узлов.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param watcher обработчик событий, не {@code null}.
     * @param stat контейнер для статистики, не {@code null}, не будет модифицирован если узла не существует.
     *
     * @return список в произвольном порядке, не {@code null}.
     *
     * @throws NoNodeException если узла не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    List<String> getChildren(String path, IWatcher watcher, NodeStat stat);

    /**
     * Устанавливает новые данные для указанного узла.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param data содержимое узла, может быть пустым массивом, {@code null} заменяется на пустой массив.
     *
     * @throws NoNodeException если узла не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    NodeStat update(String path, byte[] data);

    /**
     * Устанавливает новые данные для указанного узда в случае если указанная
     * версия совпадает с актуальной версией на сервере.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param data содержимое узла, может быть пустым массивом, {@code null} заменяется на пустой массив.
     * @param version версия узла, положительное число. Если указать {@link #ANY_VERSION},
     *      то это равносильно вызову метода {@link #update(String, byte[])}}.
     *
     * @throws BadVersionException если указанная версия не соответствует актуальной.
     * @throws NoNodeException если узла не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае иных проблем.
     */
    NodeStat update(String path, byte[] data, int version);

    /**
     * Проверяет существование узла в реесте.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @return {@code null} если узел не существует, иначе объект со статистикой.
     *
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    NodeStat exists(String path);

    /**
     * Проверяет существование узла в реесте и устанавливает обработчик событий.
     * Обработчик будет уведомлен в случае создания узла, удаления узла
     * или изменения данных узла.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param watcher обработчик событий, не {@code null}.
     *
     * @return {@code null} если узел не существует, иначе объект со статистикой.
     *
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    NodeStat exists(String path, IWatcher watcher);

    /**
     * Удаляет узел игнорируя его версию.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @throws NoNodeException если узел не существует.
     * @throws ChildrenNodeExistException в случае если у удаляемого узла существует хотя бы один дочерний.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void delete(String path);

    /**
     * Удаляет узел только если указанная параметром версия совпадает
     * с актуальной версией узла на сервере.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param version версия узла, положительное число. Если указать {@link #ANY_VERSION},
     *      то это равносильно вызову метода {@link #delete(String)}.
     *
     * @throws BadVersionException если указанная версия не соответствует актуальной.
     * @throws NoNodeException если узел не существует.
     * @throws ChildrenNodeExistException в случае если у удаляемого узла существует хотя бы один дочерний.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void delete(String path, int version);

    /**
     * Удаляет узел игнорируя его версию. Ничего не делает, если узел не существует.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @throws ChildrenNodeExistException в случае если у удаляемого узла существует хотя бы один дочерний.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void deleteSilent(String path);

    /**
     * Удаляет узел только если указанная параметром версия совпадает
     * с актуальной версией узла на сервере. Ничего не делает, если узел не
     * существует.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param version версия узла, положительное число. Если указать {@link #ANY_VERSION},
     *      то это равносильно вызову метода {@link #delete(String)}.
     *
     * @throws BadVersionException если указанная версия не соответствует актуальной.
     * @throws ChildrenNodeExistException в случае если у удаляемого узла существует хотя бы один дочерний.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void deleteSilent(String path, int version);
    
    /**
     * Удаляет узел вместе со всеми потомками игнорируя его версию и версии потомков.
     * Удаление происходит атомарно. Если в момент удаления структура узлов 
     * будет кем-то изменена, то возникнет {@link ClusterKeeperSystemException}.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @throws NoNodeException если узел не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void deleteCascade(String path);

    /**
     * Удаляет узел вместе со всеми потомками только если указанная параметром
     * версия совпадает с актуальной версией узла на сервере.
     * Удаление происходит атомарно. Если в момент удаления структура узлов 
     * будет кем-то изменена, то возникнет {@link ClusterKeeperSystemException}.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param version версия узла, положительное число. Если указать {@link #ANY_VERSION},
     *      то это равносильно вызову метода {@link #deleteCascade(String)}.
     *
     * @throws BadVersionException если указанная версия не соответствует актуальной.
     * @throws NoNodeException если узел не существует.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void deleteCascade(String path, int version);

    /**
     * Удаляет узел вместе со всеми потомками игнорируя его версию и версии
     * потомков. Ничего не делает, если узел не существует.
     * Удаление происходит атомарно. Если в момент удаления структура узлов 
     * будет кем-то изменена, то возникнет {@link ClusterKeeperSystemException}.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     *
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void deleteCascadeSilent(String path);

    /**
     * Удаляет узел вместе со всеми потомками только если указанная параметром
     * версия совпадает с актуальной версией узла на сервере. Ничего не делает,
     * если узел не существует.
     * Удаление происходит атомарно. Если в момент удаления структура узлов 
     * будет кем-то изменена, то возникнет {@link ClusterKeeperSystemException}.
     *
     * @param path полный путь узла, не {@code null} и не пустая строка.
     * @param version версия узла, положительное число. Если указать {@link #ANY_VERSION},
     *      то это равносильно вызову метода {@link #deleteCascadeSilent(String)}.
     *
     * @throws BadVersionException если указанная версия не соответствует актуальной.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    void deleteCascadeSilent(String path, int version);

    /**
     * Выполняет несколько операций последовательно и атомарно, т.е. либо
     * все операции выполнятся, либо все не выполнятся. При выполнении может
     * возникнуть исключительная ситуация, характерная для операций
     * {@code exists}, {@code create}, {@code delete} или {@code update}.
     *
     * @param operations список операций, не {@code null}.
     *
     * @return немодифицируемый список, не {@code null}, пустой список если аргумент - пустой контейнер.
     *
     * @throws ClusterKeeperException в случае ошибки при выполнении какой-либо операции.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    List<IOperationResult> multi(Iterable<IOperation> operations);

    /**
     * Выполняет несколько операций последовательно и атомарно, т.е. либо
     * все операции выполнятся, либо все не выполнятся. При выполнении может
     * возникнуть исключительная ситуация, характерная для операций
     * {@code exists}, {@code create}, {@code delete} или {@code update}.
     *
     * @param operations список операций, не {@code null}.
     *
     * @return немодифицируемый список, не {@code null}, пустой список если аргумент - пустой контейнер.
     *
     * @throws ClusterKeeperException в случае ошибки при выполнении какой-либо операции.
     * @throws KeeperConnectionLossException если потеряно сетевое соединение.
     * @throws ClusterKeeperSystemException в случае системных проблем.
     */
    List<IOperationResult> multi(IOperation... operations);
}
