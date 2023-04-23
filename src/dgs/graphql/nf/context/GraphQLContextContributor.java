package dgs.graphql.nf.context;

import graphql.GraphQLContext;
import java.util.Map;
import dgs.graphql.nf.internal.DgsRequestData;

/**
 * For each bean implementing this interface found, the framework will call the [contribute] method for every request.
 * The [contribute] method is then able to use the [GraphQLContext.Builder] to provide additional entries to place in the context.
 */
public interface GraphQLContextContributor {
    public void contribute( GraphQLContext.Builder builder,  Map<String,Object> extensions,  DgsRequestData requestData);
}

