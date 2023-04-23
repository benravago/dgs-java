package dgs.graphql.nf;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DelegatingDataFetchingEnvironment;

import java.util.Arrays;

import dgs.graphql.nf.context.DgsContext;
import dgs.graphql.nf.exceptions.MultipleDataLoadersDefinedException;
import dgs.graphql.nf.exceptions.NoDataLoaderFoundException;
import dgs.graphql.nf.internal.utils.DataLoaderNameUtil;

import org.dataloader.DataLoader;

public class DgsDataFetchingEnvironment extends DelegatingDataFetchingEnvironment {

    public DgsDataFetchingEnvironment(DataFetchingEnvironment dfe) {
       super(dfe);
    }

    public DgsContext getDgsContext() {
        return DgsContext.from(this);
    }

    public <K, V> DataLoader<K, V> getDataLoader( Class<?> loaderClass) {
        var annotation = loaderClass.getAnnotation(DgsDataLoader.class);
        if (annotation != null) {
            return delegateEnvironment.getDataLoader(DataLoaderNameUtil.getDataLoaderName(loaderClass, annotation));
        } else {
            var loaders = Arrays.stream(loaderClass.getFields()).filter(it -> it.isAnnotationPresent(DgsDataLoader.class)).toList();
            if (loaders.size() > 1) throw new MultipleDataLoadersDefinedException(loaderClass);
            var loaderField = loaders.get(0);
            if (loaderField == null) throw new NoDataLoaderFoundException(loaderClass);
            var theAnnotation = loaderField.getAnnotation(DgsDataLoader.class);
            var loaderName = theAnnotation.name();
            return delegateEnvironment.getDataLoader(loaderName);
        }
    }

}

