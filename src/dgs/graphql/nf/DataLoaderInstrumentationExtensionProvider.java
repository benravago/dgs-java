package dgs.graphql.nf;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;

public interface DataLoaderInstrumentationExtensionProvider {
    public BatchLoader<?, ?> provide( BatchLoader<?, ?> var1,  String var2);
    public BatchLoaderWithContext<?, ?> provide( BatchLoaderWithContext<?, ?> var1,  String var2);
    public MappedBatchLoader<?, ?> provide( MappedBatchLoader<?, ?> var1,  String var2);
    public MappedBatchLoaderWithContext<?, ?> provide( MappedBatchLoaderWithContext<?, ?> var1,  String var2);
}

