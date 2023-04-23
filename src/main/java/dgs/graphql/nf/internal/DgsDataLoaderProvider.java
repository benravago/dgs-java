package dgs.graphql.nf.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import dgs.graphql.nf.DataLoaderInstrumentationExtensionProvider;
import dgs.graphql.nf.DgsComponent;
import dgs.graphql.nf.DgsDataLoader;
import dgs.graphql.nf.DgsDataLoaderRegistryConsumer;
import dgs.graphql.nf.DgsDispatchPredicate;
import dgs.graphql.nf.exceptions.DgsUnnamedDataLoaderOnFieldException;
import dgs.graphql.nf.exceptions.InvalidDataLoaderTypeException;
import dgs.graphql.nf.exceptions.UnsupportedSecuredDataLoaderException;
import dgs.graphql.nf.internal.utils.DataLoaderNameUtil;
import dgs.graphql.nf.support.Kt;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;
import org.dataloader.registries.DispatchPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

/**
 * Framework implementation class responsible for finding and configuring data loaders.
 */
public class DgsDataLoaderProvider {

    record LoaderHolder<T> (
        T theLoader,
        DgsDataLoader annotation,
        String name,
        DispatchPredicate dispatchPredicate
    ) {}

    private final List<LoaderHolder<BatchLoader<?, ?>>> batchLoaders;
    private final List<LoaderHolder<BatchLoaderWithContext<?, ?>>> batchLoadersWithContext;
    private final List<LoaderHolder<MappedBatchLoader<?, ?>>> mappedBatchLoaders;
    private final List<LoaderHolder<MappedBatchLoaderWithContext<?, ?>>> mappedBatchLoadersWithContext;

    private final ApplicationContext applicationContext;

    private static final Logger logger = LoggerFactory.getLogger(DgsDataLoaderProvider.class);

    public DgsDataLoaderProvider(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.batchLoaders = new ArrayList();
        this.batchLoadersWithContext = new ArrayList();
        this.mappedBatchLoaders = new ArrayList();
        this.mappedBatchLoadersWithContext = new ArrayList();
    }


    public DataLoaderRegistry buildRegistry() {
        return buildRegistryWithContextSupplier(() -> null);
    }


    public <T> DataLoaderRegistry buildRegistryWithContextSupplier(Supplier<T> contextSupplier) {
        long startTime = System.currentTimeMillis();
        var dgsDataLoaderRegistry = new DgsDataLoaderRegistry();

        batchLoaders.forEach(it -> {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry)
                );
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(it.name, createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry), it.dispatchPredicate);
            }
        });
        mappedBatchLoaders.forEach(it -> {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry)
                );
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, dgsDataLoaderRegistry),
                    it.dispatchPredicate
                );
            }
        });
        batchLoadersWithContext.forEach(it -> {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry)
                );
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry),
                    it.dispatchPredicate
                );
            }
        });
        mappedBatchLoadersWithContext.forEach(it -> {
            if (it.dispatchPredicate == null) {
                dgsDataLoaderRegistry.register(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry)
                );
            } else {
                dgsDataLoaderRegistry.registerWithDispatchPredicate(
                    it.name,
                    createDataLoader(it.theLoader, it.annotation, it.name, contextSupplier, dgsDataLoaderRegistry),
                    it.dispatchPredicate
                );
            }
        });

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        logger.debug("Created DGS dataloader registry in {}ms", totalTime);

        return dgsDataLoaderRegistry;
    }

    // TODO: review this - is it relevant fo Spring ??
    // @jakarta.annotations.PostConstruct
    public void findDataLoaders() {
        this.addDataLoaderComponents();
        this.addDataLoaderFields();
    }

    void addDataLoaderFields() {
        applicationContext.getBeansWithAnnotation(DgsComponent.class).values().forEach(dgsComponent -> {
            var javaClass = AopUtils.getTargetClass(dgsComponent);

            Arrays.stream(javaClass.getDeclaredFields()).filter(it -> it.isAnnotationPresent(DgsDataLoader.class))
                .forEach(field -> {
                    if (AopUtils.isAopProxy(dgsComponent)) {
                        throw new UnsupportedSecuredDataLoaderException(dgsComponent.getClass());
                    }

                    var annotation = field.getAnnotation(DgsDataLoader.class);
                    ReflectionUtils.makeAccessible(field);

                    if (annotation.name().equals(DgsDataLoader.GENERATE_DATA_LOADER_NAME)) {
                        throw new DgsUnnamedDataLoaderOnFieldException(field);
                    }

                    var get = Kt.call(() -> field.get(dgsComponent));
                    var createHolder = new LoaderHolder(get, annotation, annotation.name(), null);
                    switch (get) {
                        case BatchLoader batchLoader -> batchLoaders.add(createHolder);
                        case BatchLoaderWithContext batchLoaderWithContext -> batchLoadersWithContext.add(createHolder);
                        case MappedBatchLoader mappedBatchLoader -> mappedBatchLoaders.add(createHolder);
                        case MappedBatchLoaderWithContext mappedBatchLoaderWithContext -> mappedBatchLoadersWithContext.add(createHolder);
                        default -> throw new InvalidDataLoaderTypeException(dgsComponent.getClass());
                    }
                });
        });
    }

    void addDataLoaderComponents() {
        var dataLoaders = applicationContext.getBeansWithAnnotation(DgsDataLoader.class);
        dataLoaders.values().forEach(dgsComponent -> {
            var javaClass = AopUtils.getTargetClass(dgsComponent);
            var annotation = javaClass.getAnnotation(DgsDataLoader.class);
            var f = Arrays.stream(javaClass.getDeclaredFields()).filter(it -> it.isAnnotationPresent(DgsDispatchPredicate.class)).findFirst();
            if (f.isPresent()) {
                var predicateField = f.get();
                ReflectionUtils.makeAccessible(predicateField);
                var d = Kt.call(() -> predicateField.get(dgsComponent));
                if (d instanceof DispatchPredicate dispatchPredicate) {
                    addDataLoaders(dgsComponent, javaClass, annotation, dispatchPredicate);
                }
            } else {
                addDataLoaders(dgsComponent, javaClass, annotation, null);
            }
        });
    }

    <T> void addDataLoaders(T dgsComponent, Class<?> targetClass, DgsDataLoader annotation, DispatchPredicate dispatchPredicate) {
        var createHolder = new LoaderHolder(dgsComponent, annotation, DataLoaderNameUtil.getDataLoaderName(targetClass, annotation), dispatchPredicate);
        switch (dgsComponent) {
            case BatchLoader batchLoader -> batchLoaders.add(createHolder);
            case BatchLoaderWithContext batchLoaderWithContext -> batchLoadersWithContext.add(createHolder);
            case MappedBatchLoader mappedBatchLoader -> mappedBatchLoaders.add(createHolder);
            case MappedBatchLoaderWithContext mappedBatchLoaderWithContext -> mappedBatchLoadersWithContext.add(createHolder);
            default -> throw new InvalidDataLoaderTypeException(dgsComponent.getClass());
        }
    }

    DataLoader<?, ?> createDataLoader(
        BatchLoader<?, ?> batchLoader,
        DgsDataLoader dgsDataLoader,
        String dataLoaderName,
        DataLoaderRegistry dataLoaderRegistry
    ) {
        var options = dataLoaderOptions(dgsDataLoader);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer dgsDataLoaderRegistryConsumer) {
            dgsDataLoaderRegistryConsumer.setDataLoaderRegistry(dataLoaderRegistry);
        }

        var extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName);
        return DataLoaderFactory.newDataLoader(extendedBatchLoader, options);
    }

    DataLoader<?, ?> createDataLoader(
        MappedBatchLoader<?, ?> batchLoader,
        DgsDataLoader dgsDataLoader,
        String dataLoaderName,
        DataLoaderRegistry dataLoaderRegistry
    ) {
        var options = dataLoaderOptions(dgsDataLoader);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer dgsDataLoaderRegistryConsumer) {
            dgsDataLoaderRegistryConsumer.setDataLoaderRegistry(dataLoaderRegistry);
        }
        var extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName);

        return DataLoaderFactory.newMappedDataLoader(extendedBatchLoader, options);
    }

    <T> DataLoader<?, ?> createDataLoader(
        BatchLoaderWithContext<?, ?> batchLoader,
        DgsDataLoader dgsDataLoader,
        String dataLoaderName,
        Supplier<T> supplier,
        DataLoaderRegistry dataLoaderRegistry
    ) {
        var options = dataLoaderOptions(dgsDataLoader)
            .setBatchLoaderContextProvider(supplier::get);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer dgsDataLoaderRegistryConsumer) {
            dgsDataLoaderRegistryConsumer.setDataLoaderRegistry(dataLoaderRegistry);
        }

        var extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName);
        return DataLoaderFactory.newDataLoader(extendedBatchLoader, options);
    }

    <T> DataLoader<?, ?> createDataLoader(
        MappedBatchLoaderWithContext<?, ?> batchLoader,
        DgsDataLoader dgsDataLoader,
        String dataLoaderName,
        Supplier<T> supplier,
        DataLoaderRegistry dataLoaderRegistry
    ) {
        var options = dataLoaderOptions(dgsDataLoader)
            .setBatchLoaderContextProvider(supplier::get);

        if (batchLoader instanceof DgsDataLoaderRegistryConsumer dgsDataLoaderRegistryConsumer) {
            dgsDataLoaderRegistryConsumer.setDataLoaderRegistry(dataLoaderRegistry);
        }

        var extendedBatchLoader = wrappedDataLoader(batchLoader, dataLoaderName);
        return DataLoaderFactory.newMappedDataLoader(extendedBatchLoader, options);
    }

    DataLoaderOptions dataLoaderOptions(DgsDataLoader annotation) {
        var options = new DataLoaderOptions()
            .setBatchingEnabled(annotation.batching())
            .setCachingEnabled(annotation.caching());
        if (annotation.maxBatchSize() > 0) {
            options.setMaxBatchSize(annotation.maxBatchSize());
        }
        return options;
    }

    /* synthetic */
    <T> T wrappedDataLoader(T loader, String name) {
        try {
            var stream = applicationContext
                .getBeanProvider(DataLoaderInstrumentationExtensionProvider.class)
                .orderedStream();

            return (T) switch (loader) {
                case BatchLoader w -> {
                    var wrappedBatchLoader = new BatchLoader[]{w};
                    stream.forEach(it -> wrappedBatchLoader[0] = it.provide(wrappedBatchLoader[0], name));
                    yield wrappedBatchLoader[0];
                }
                case BatchLoaderWithContext w -> {
                    var wrappedBatchLoader = new BatchLoaderWithContext[]{w};
                    stream.forEach(it -> wrappedBatchLoader[0] = it.provide(wrappedBatchLoader[0], name));
                    yield wrappedBatchLoader[0];
                }
                case MappedBatchLoader w -> {
                    var wrappedBatchLoader = new MappedBatchLoader[]{w};
                    stream.forEach(it -> wrappedBatchLoader[0] = it.provide(wrappedBatchLoader[0], name));
                    yield wrappedBatchLoader[0];
                }
                case MappedBatchLoaderWithContext w -> {
                    var wrappedBatchLoader = new MappedBatchLoaderWithContext[]{w};
                    stream.forEach(it -> wrappedBatchLoader[0] = it.provide(wrappedBatchLoader[0], name));
                    yield wrappedBatchLoader[0];
                }
                default -> throw new IllegalArgumentException("not a loader: " + loader );
            };
        } catch (NoSuchBeanDefinitionException ex) {
            logger.debug("Unable to wrap the [{} : {}]", name, loader, ex);
        }
        return loader;
    }

}

