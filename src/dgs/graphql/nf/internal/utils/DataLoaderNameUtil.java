package dgs.graphql.nf.internal.utils;

import dgs.graphql.nf.DgsDataLoader;

public class DataLoaderNameUtil {

    /**
     * When the [annotation]'s [DgsDataLoader.name] is equal to [DgsDataLoader.GENERATE_DATA_LOADER_NAME],
     * the [clazz]'s [Class.getSimpleName] will be used.
     * In all other cases the [DgsDataLoader.name] method will be called on [annotation].
     *
     * This method does not verify that [annotation] belongs to [clazz] for performance reasons.
     */
    public static final String getDataLoaderName(Class<?> clazz, DgsDataLoader annotation) {
        var name = annotation.name();
        return name.equals(DgsDataLoader.GENERATE_DATA_LOADER_NAME) ? clazz.getSimpleName() : name;
    }

}

