package dgs.graphql.nf.internal;

import dgs.graphql.nf.DgsDataFetchingEnvironment;

public interface DataFetcherResultProcessor {

    boolean supportsType(Object originalResult);
    Object process(Object originalResult, DgsDataFetchingEnvironment dfe);
}

