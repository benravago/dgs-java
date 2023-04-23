package dgs.graphql.nf.context;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;

import dgs.graphql.nf.internal.DgsRequestData;

import reactor.util.context.ContextView;

public class ReactiveDgsContext extends DgsContext {

    public ReactiveDgsContext( Object customContext,  DgsRequestData requestData,  ContextView reactorContext) {
        super(customContext, requestData);
        this.reactorContext = reactorContext;
    }

    private final ContextView reactorContext;

    public ContextView getReactorContext() {
        return this.reactorContext;
    }

    public static ReactiveDgsContext from(GraphQLContext graphQLContext) {
        return (ReactiveDgsContext) DgsContext.from(graphQLContext);
    }

    public static ReactiveDgsContext from(DataFetchingEnvironment dfe) {
        return from(dfe.getGraphQlContext());
    }

}

