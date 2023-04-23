package dgs.graphql.nf.internal;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.registries.DispatchPredicate;
import org.dataloader.registries.ScheduledDataLoaderRegistry;
import org.dataloader.stats.Statistics;

/**
 * The DgsDataLoaderRegistry is a registry of DataLoaderRegistry instances. It supports specifying
 * DispatchPredicate on a per data loader basis, specified using @DispatchPredicate annotation. It creates an instance
 * of a ScheduledDataLoaderRegistry for every data loader that is registered and delegates to the mapping instance of
 * the registry based on the key. We need to create a registry per data loader since a DispatchPredicate is applicable
 * for an instance of the ScheduledDataLoaderRegistry.
 * https://github.com/graphql-java/java-dataloader#scheduled-dispatching
 */
public class DgsDataLoaderRegistry extends DataLoaderRegistry {

    private final Map<String, DataLoaderRegistry> scheduledDataLoaderRegistries = new ConcurrentHashMap();
    private final DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();

    /**
     * This will register a new dataloader
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     *
     * @return this registry
     */
    @Override
    public DataLoaderRegistry register(String key, DataLoader<?,?> dataLoader) {
        dataLoaderRegistry.register(key, dataLoader);
        return this;
    }

    /**
     * This will register a new dataloader with a dispatch predicate set up for that loader
     *
     * @param key        the key to put the data loader under
     * @param dataLoader the data loader to register
     *
     * @return this registry
     */
    public DataLoaderRegistry registerWithDispatchPredicate(String key, DataLoader<?,?> dataLoader, DispatchPredicate dispatchPredicate) {
        var registry = ScheduledDataLoaderRegistry.newScheduledRegistry().register(key, dataLoader)
            .dispatchPredicate(dispatchPredicate)
            .build();
        scheduledDataLoaderRegistries.putIfAbsent(key, registry);
        return this;
    }

    /**
     * Computes a data loader if absent or return it if it was
     * already registered at that key.
     *
     *
     * Note: The entire method invocation is performed atomically,
     * so the function is applied at most once per key.
     *
     * @param key             the key of the data loader
     * @param mappingFunction the function to compute a data loader
     * @param <K>             the type of keys
     * @param <V>             the type of values
     *
     * @return a data loader
     */
    @Override
    public <K, V> DataLoader<K, V> computeIfAbsent(String key, Function<String, DataLoader<?,?>> mappingFunction) {
        // we do not support this method for registering with dispatch predicates
        return dataLoaderRegistry.computeIfAbsent(key, mappingFunction);
    }

    /**
     *  This operation is not supported since we cannot store a dataloader registry without a key.
     */
    @Override
    public DataLoaderRegistry combine(DataLoaderRegistry registry) {
        throw new UnsupportedOperationException("Cannot combine a DgsDataLoaderRegistry with another registry");
    }

    /**
     * @return the currently registered data loaders
     */
    @Override
    public List<DataLoader<?, ?>> getDataLoaders() {
        return Stream
            .concat(scheduledDataLoaderRegistries.entrySet().stream().flatMap(it -> it.getValue().getDataLoaders().stream()),
                    dataLoaderRegistry.getDataLoaders().stream())
            .toList();
    }

    /**
     * @return the currently registered data loaders as a map
     */
    @Override
    public Map<String, DataLoader<?,?>> getDataLoadersMap() {
        var dataLoadersMap = new LinkedHashMap<String, DataLoader<?,?>>();
        for (var it:scheduledDataLoaderRegistries.entrySet()) {
            dataLoadersMap.putAll(it.getValue().getDataLoadersMap());
        }
        dataLoadersMap.putAll(dataLoaderRegistry.getDataLoadersMap());
        return dataLoadersMap;
    }

    /**
     * This will unregister a new dataloader
     *
     * @param key the key of the data loader to unregister
     *
     * @return this registry
     */
    @Override
    public DataLoaderRegistry unregister(String key) {
        scheduledDataLoaderRegistries.remove(key);
        dataLoaderRegistry.unregister(key);
        return this;
    }

    /**
     * Returns the dataloader that was registered under the specified key
     *
     * @param key the key of the data loader
     * @param <K> the type of keys
     * @param <V> the type of values
     *
     * @return a data loader or null if its not present
     */
    @Override
    public <K, V> DataLoader<K, V> getDataLoader(String key) {
        if (dataLoaderRegistry.getKeys().contains(key)) {
            return dataLoaderRegistry.getDataLoader(key);
        }
        if (scheduledDataLoaderRegistries.containsKey(key)) {
            var registry = scheduledDataLoaderRegistries.get(key);
            return registry != null ? registry.getDataLoader(key) : null;
        }
        return null;
    }

    @Override
    public Set<String> getKeys() {
        var keys = new HashSet(scheduledDataLoaderRegistries.keySet());
        keys.addAll(dataLoaderRegistry.getKeys());
        return keys;
    }

    /**
     * This will be called [org.dataloader.DataLoader.dispatch] on each of the registered
     * [org.dataloader.DataLoader]s
     */
    @Override
    public void dispatchAll() {
        for (var it:scheduledDataLoaderRegistries.entrySet()) {
            it.getValue().dispatchAll();
        }
        dataLoaderRegistry.dispatchAll();
    }

    /**
     * Similar to [DataLoaderRegistry.dispatchAll], this calls [org.dataloader.DataLoader.dispatch] on
     * each of the registered [org.dataloader.DataLoader]s, but returns the number of dispatches.
     *
     * @return total number of entries that were dispatched from registered [org.dataloader.DataLoader]s.
     */
    @Override
    public int dispatchAllWithCount() {
        var sum = 0;
        for (var it:scheduledDataLoaderRegistries.entrySet()) {
            sum += it.getValue().dispatchAllWithCount();
        }
        sum += this.dataLoaderRegistry.dispatchAllWithCount();
        return sum;
    }

    /**
     * @return The sum of all batched key loads that need to be dispatched from all registered
     * [org.dataloader.DataLoader]s
     */
    @Override
    public int dispatchDepth() {
        var totalDispatchDepth = 0;
        for (var it:scheduledDataLoaderRegistries.entrySet()) {
            totalDispatchDepth += it.getValue().dispatchDepth();
        }
        totalDispatchDepth += this.dataLoaderRegistry.dispatchDepth();
        return totalDispatchDepth;
    }

    public Statistics getStatistics() {
        var stats = new Statistics();
        for (var it:scheduledDataLoaderRegistries.entrySet()) {
            stats = stats.combine(it.getValue().getStatistics());
        }
        stats = stats.combine(dataLoaderRegistry.getStatistics());
        return stats;
    }

}

