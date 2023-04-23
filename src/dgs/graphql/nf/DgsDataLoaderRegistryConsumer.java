package dgs.graphql.nf;

import org.dataloader.DataLoaderRegistry;

/**
 * Interface indicating that this DataLoader wants to be call-backed with a reference to the DataLoaderReference.
 */
public interface DgsDataLoaderRegistryConsumer {

    /**
     * Callback to retrieve the DataLoaderRegistry instance.
     * @param dataLoaderRegistry Typically this is stored as an instance variable for later use.
     */
    void setDataLoaderRegistry(DataLoaderRegistry dataLoaderRegistry);
}
