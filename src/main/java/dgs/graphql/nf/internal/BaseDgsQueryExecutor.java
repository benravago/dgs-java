package dgs.graphql.nf.internal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import dgs.graphql.nf.DgsExecutionResult;
import dgs.graphql.nf.context.DgsContext;
import dgs.graphql.nf.exceptions.DgsBadRequestException;
import dgs.graphql.nf.support.Kt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;

public class BaseDgsQueryExecutor {
    private BaseDgsQueryExecutor() {}

    public static final BaseDgsQueryExecutor INSTANCE = new BaseDgsQueryExecutor();

    private static final Logger logger = LoggerFactory.getLogger(BaseDgsQueryExecutor.class);

    public static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static final ParseContext parseContext =
        JsonPath.using(
            Configuration.builder()
                .jsonProvider(new JacksonJsonProvider(new ObjectMapper()))
                .mappingProvider(new JacksonMappingProvider(objectMapper)).build()
                .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
        );

    public CompletableFuture<ExecutionResult> baseExecute(
        String query,
        Map<String, Object> variables,
        Map<String, Object> extensions,
        String operationName,
        DgsContext dgsContext,
        GraphQLSchema graphQLSchema,
        DgsDataLoaderProvider dataLoaderProvider,
        Instrumentation instrumentation,
        ExecutionStrategy queryExecutionStrategy,
        ExecutionStrategy mutationExecutionStrategy,
        Optional<ExecutionIdProvider> idProvider,
        PreparsedDocumentProvider preparsedDocumentProvider
    ) {
        var inputVariables = variables != null ? variables : Collections.EMPTY_MAP;

        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(
                DgsExecutionResult
                    .builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .executionResult(
                        ExecutionResultImpl
                            .newExecutionResult()
                            .errors(
                                List.of(
                                    DgsBadRequestException
                                        .NULL_OR_EMPTY_QUERY_EXCEPTION
                                        .toGraphQlError(null)
                                )
                            )
                        ).build()
                );
        }

        var graphQLBuilder =
            GraphQL.newGraphQL(graphQLSchema)
                .queryExecutionStrategy(queryExecutionStrategy)
                .mutationExecutionStrategy(mutationExecutionStrategy);

        if (preparsedDocumentProvider != null) graphQLBuilder.preparsedDocumentProvider(preparsedDocumentProvider);
        if (instrumentation != null) graphQLBuilder.instrumentation(instrumentation);
        if (idProvider.isPresent()) graphQLBuilder.executionIdProvider(idProvider.get());

        var graphQL = graphQLBuilder.build();
        var graphQLContextFuture = new CompletableFuture<GraphQLContext>();
        var dataLoaderRegistry = dataLoaderProvider.buildRegistryWithContextSupplier(() -> Kt.call(() -> graphQLContextFuture.get()));

        try {
            var executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .operationName(operationName)
                .variables(inputVariables)
                .dataLoaderRegistry(dataLoaderRegistry)
                .context(dgsContext) // Deprecated
                .graphQLContext(dgsContext)
                .extensions((extensions != null ? extensions : Collections.EMPTY_MAP))
                .build();
            graphQLContextFuture.complete(executionInput.getGraphQLContext());
            return graphQL.executeAsync(executionInput);
        }
        catch (Exception e) {
            logger.error("Encountered an exception while handling query " + query, e);
            var errors = e instanceof GraphQLError graphQLError ? List.of(graphQLError) : Collections.EMPTY_LIST;
            return CompletableFuture.completedFuture(new ExecutionResultImpl(null, errors));
        }
    }

}

