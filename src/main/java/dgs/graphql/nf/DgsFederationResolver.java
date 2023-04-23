package dgs.graphql.nf;

import graphql.schema.DataFetcher;
import graphql.schema.TypeResolver;

/**
 * Required only when federation is used.
 * For federation the frameworks needs a mapping from __typename to an actual type which is done by the entitiesFetcher.
 * The typeResolver takes an instance of a concrete type, and returns its type name as defined in the schema.
 */
public interface DgsFederationResolver {
    public DataFetcher<Object> entitiesFetcher();
    public TypeResolver typeResolver();
}

