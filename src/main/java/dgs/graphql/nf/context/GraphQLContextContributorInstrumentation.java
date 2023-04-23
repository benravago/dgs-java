package dgs.graphql.nf.context;

import graphql.GraphQLContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;

import java.util.List;

/**
 * Instrumentation that allows GraphQLContextContributor's to contribute to values stored in the GraphQLContext object.
 * For each contributor, invoke the GraphQLContextContributor's contribute method, and then put the resulting contents
 * of the intermediate GraphQLContext into the existing GraphQLContext.
 *
 * @see com.netflix.graphql.dgs.context.GraphQLContextContributor.contribute()
 */
public class GraphQLContextContributorInstrumentation extends SimpleInstrumentation {

    private final List<? extends GraphQLContextContributor> graphQLContextContributors;

    public GraphQLContextContributorInstrumentation( List<? extends GraphQLContextContributor> graphQLContextContributors) {
        this.graphQLContextContributors = graphQLContextContributors;
    }

    /**
     * createState is the very first method invoked in an Instrumentation, and thus is where this logic is placed to
     * contribute to the GraphQLContext as early as possible.
     */
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        var graphqlContext = parameters.getExecutionInput().getGraphQLContext();
        if (graphqlContext != null && graphQLContextContributors.iterator().hasNext()) {
            var extensions = parameters.getExecutionInput().getExtensions();
            var requestData = DgsContext.from(graphqlContext).getRequestData();
            var builderForContributors = GraphQLContext.newContext();
            graphQLContextContributors.forEach(it -> it.contribute(builderForContributors, extensions, requestData) );
            graphqlContext.putAll(builderForContributors);
        }

        return super.createState(parameters);
    }

}

