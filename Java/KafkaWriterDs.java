---
import java.util.List;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
---
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Providers;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;

/**
 * Класс предназначен для использования декларативными сервисами. Запускакет/останавливает KafkaReader.
 */
@Component(immediate = true, name = "KafkaWriter")
public class KafkaWriterDs extends AbstractGuiceComponent
{
    private IConfiguratorFactory configFactory;
    private IShardRuntime shardRuntime;
    private IWriterRuntime writerRuntime;

    @Override
    protected Optional<Module> createModule()
    {
        // создание модуля для бандла
        return Optional.<Module> of(new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(IConfiguratorFactory.class).toProvider(Providers.of(configFactory));
                bind(IWriterRuntime.class).toProvider(Providers.of(writerRuntime)).in(Scopes.SINGLETON);
                bind(IShardRuntime.class).toProvider(Providers.of(shardRuntime)).in(Scopes.SINGLETON);

                bind(KeeperConfig.class).toProvider(ZooKeeperConfigProvider.class).in(Scopes.SINGLETON);
                bind(KafkaWriter.class).toProvider(KafkaWriterProvider.class).in(Scopes.SINGLETON);
                bind(IKafkaWriter.class).to(KafkaWriter.class);
                bind(WriterService.class).in(Scopes.SINGLETON);
                bind(WriterWatchListener.class).in(Scopes.SINGLETON);
                bind(WritersSelector.class).in(Scopes.SINGLETON);
            }
        });
    }

    @Reference(policy = STATIC, cardinality = MANDATORY)
    public void setConfiguratorFactory(IConfiguratorFactory configFactory)
    {
        this.configFactory = configFactory;
    }

    @Reference(policy = STATIC, cardinality = MANDATORY)
    public void setWriterRuntime(IWriterRuntime writerRuntime)
    {
        this.writerRuntime = writerRuntime;
    }

    @Reference(policy = STATIC, cardinality = MANDATORY)
    public void setShardRuntime(IShardRuntime shardRuntime)
    {
        this.shardRuntime = shardRuntime;
    }

    @Override
    protected List<Class<? extends ILifeCycle<?>>> getControlledServices()
    {
        // определение порядка старта и остановки внутренних подсистем
        return ImmutableList.<Class<? extends ILifeCycle<?>>> of(WriterService.class);
    }

    @Override
    protected void configure()
    {
        addPublication(IKafkaWriter.class, KafkaWriter.class);
    }
}
