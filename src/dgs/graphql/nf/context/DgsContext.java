package dgs.graphql.nf.context;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.schema.DataFetchingEnvironment;

import java.util.function.Consumer;

import dgs.graphql.nf.internal.DgsRequestData;

import org.dataloader.BatchLoaderEnvironment;

/**
 * Context class that is created per request, and is added to both DataFetchingEnvironment and BatchLoaderEnvironment.
 * Custom data can be added by providing a [DgsCustomContextBuilder].
 */
public class DgsContext implements Consumer<GraphQLContext.Builder> {

    public DgsContext(Object customContext, DgsRequestData requestData) {
        this.customContext = customContext;
        this.requestData = requestData;
    }

    private final Object customContext;
    private final DgsRequestData requestData;

    public Object getCustomContext() {
        return this.customContext;
    }

    public DgsRequestData getRequestData() {
        return this.requestData;
    }

    private enum GraphQLContextKey { DGS_CONTEXT_KEY }

    public static DgsContext from(GraphQLContext graphQLContext) {
        return graphQLContext.get(GraphQLContextKey.DGS_CONTEXT_KEY);
    }

    public static DgsContext from(DataFetchingEnvironment dfe) {
        return from(dfe.getGraphQlContext());
    }

    public static DgsContext from(ExecutionInput ei) {
        return from(ei.getGraphQLContext());
    }

    public static DgsContext from(InstrumentationCreateStateParameters p) {
        return from(p.getExecutionInput().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationExecuteOperationParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationExecutionParameters p) {
        return from(p.getGraphQLContext());
    }

    public static DgsContext from(InstrumentationExecutionStrategyParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationFieldCompleteParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationFieldFetchParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationFieldParameters p) {
        return from(p.getExecutionContext().getGraphQLContext());
    }

    public static DgsContext from(InstrumentationValidationParameters p) {
        return from(p.getGraphQLContext());
    }

    public static <T> T getCustomContext(Object context) {
        return switch(context) {
            case DgsContext dgsContext -> (T) dgsContext.getCustomContext();
            case GraphQLContext graphQLContext -> getCustomContext(from(graphQLContext));
            default -> throw new RuntimeException("The context object passed to getCustomContext is not a DgsContext. It is a " + context.getClass().getName() + " instead.");
        };
    }

    public static <T> T getCustomContext(DataFetchingEnvironment dataFetchingEnvironment) {
        var dgsContext = from(dataFetchingEnvironment);
        return getCustomContext(dgsContext);
    }

    public static <T> T getCustomContext(BatchLoaderEnvironment batchLoaderEnvironment) {
        var dgsContext = (GraphQLContext)batchLoaderEnvironment.getContext();
        return getCustomContext(dgsContext);
    }

    public static DgsRequestData getRequestData(DataFetchingEnvironment dataFetchingEnvironment) {
        var dgsContext = from(dataFetchingEnvironment);
        return dgsContext.getRequestData();
    }

    public static DgsRequestData getRequestData(BatchLoaderEnvironment batchLoaderEnvironment) {
        var dgsContext = (DgsContext)batchLoaderEnvironment.getContext();
        return dgsContext.getRequestData();
    }

    @Override
    public void accept(GraphQLContext.Builder contextBuilder) {
        contextBuilder.put(GraphQLContextKey.DGS_CONTEXT_KEY, this);
    }

}

