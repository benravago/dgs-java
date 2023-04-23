package dgs.graphql.nf.internal;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingException;

import graphql.ExecutionResult;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.NonNullableFieldWasNullError;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dgs.graphql.nf.DgsQueryExecutor;
import dgs.graphql.nf.exceptions.DgsQueryExecutionDataExtractionException;
import dgs.graphql.nf.internal.DefaultDgsGraphQLContextBuilder.DgsWebMvcRequestData;
import dgs.graphql.nf.exceptions.QueryException;
import dgs.graphql.nf.support.Kt;
import static dgs.graphql.nf.internal.BaseDgsQueryExecutor.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Main Query executing functionality. This should be reused between different transport protocols and the testing framework.
 */
public class DefaultDgsQueryExecutor implements DgsQueryExecutor {

    private final AtomicReference<GraphQLSchema> schema;
    private final DgsSchemaProvider schemaProvider;
    private final DgsDataLoaderProvider dataLoaderProvider;
    private final DefaultDgsGraphQLContextBuilder contextBuilder;
    private final Instrumentation instrumentation;
    private final ExecutionStrategy queryExecutionStrategy;
    private final ExecutionStrategy mutationExecutionStrategy;
    private final Optional<ExecutionIdProvider> idProvider;
    private final ReloadSchemaIndicator reloadIndicator;
    private final PreparsedDocumentProvider preparsedDocumentProvider;
    private final QueryValueCustomizer queryValueCustomizer;
    private final DgsQueryExecutorRequestCustomizer requestCustomizer;

    public DefaultDgsQueryExecutor(
        GraphQLSchema defaultSchema,
        DgsSchemaProvider schemaProvider,
        DgsDataLoaderProvider dataLoaderProvider,
        DefaultDgsGraphQLContextBuilder contextBuilder,
        Instrumentation instrumentation,
        ExecutionStrategy queryExecutionStrategy,
        ExecutionStrategy mutationExecutionStrategy,
        Optional<ExecutionIdProvider> idProvider,
        ReloadSchemaIndicator reloadIndicator,
        PreparsedDocumentProvider preparsedDocumentProvider,
        QueryValueCustomizer queryValueCustomizer,
        DgsQueryExecutorRequestCustomizer requestCustomizer
    ) {
        this.schemaProvider = schemaProvider;
        this.dataLoaderProvider = dataLoaderProvider;
        this.contextBuilder = contextBuilder;
        this.instrumentation = instrumentation;
        this.queryExecutionStrategy = queryExecutionStrategy;
        this.mutationExecutionStrategy = mutationExecutionStrategy;
        this.idProvider = idProvider;
        this.reloadIndicator = reloadIndicator;
        this.preparsedDocumentProvider = preparsedDocumentProvider;
        this.queryValueCustomizer = queryValueCustomizer;
        this.requestCustomizer = requestCustomizer;
        this.schema = new AtomicReference<GraphQLSchema>(defaultSchema);
    }

    public final AtomicReference<GraphQLSchema> getSchema() {
        return this.schema;
    }

    @Override
    public ExecutionResult execute(
        String query,
        Map<String, Object> variables,
        Map<String, Object> extensions,
        HttpHeaders headers,
        String operationName,
        WebRequest webRequest
    ) {
        var graphQLSchema = reloadIndicator.reloadSchema()
            ? schema.updateAndGet(it -> schemaProvider.schema(null,null))
            : schema.get();

        var request = requestCustomizer.apply(
            (webRequest != null ? webRequest : (WebRequest)(RequestContextHolder.getRequestAttributes())),
            headers );

        var dgsContext = contextBuilder.build(
            new DgsWebMvcRequestData(extensions, headers, request));

        var executionResult =
            BaseDgsQueryExecutor.INSTANCE.baseExecute(
                queryValueCustomizer.apply(query),
                variables,
                extensions,
                operationName,
                dgsContext,
                graphQLSchema,
                dataLoaderProvider,
                instrumentation,
                queryExecutionStrategy,
                mutationExecutionStrategy,
                idProvider,
                preparsedDocumentProvider
            );

        // Check for NonNullableFieldWasNull errors, and log them explicitly because they don't run through the exception handlers.
        var result = Kt.call(() -> executionResult.get());
        if (result.getErrors().size() > 0) {
            var nullValueError = result.getErrors().stream().filter(it -> it instanceof NonNullableFieldWasNullError).findFirst();
            if (nullValueError.isPresent()) {
                logger.error(nullValueError.get().getMessage());
            }
        }
        return result;
    }

    @Override
    public <T> T executeAndExtractJsonPath(String query, String jsonPath, Map<String, Object> variables) {
        return JsonPath.read(getJsonResult(query, variables, null, null), jsonPath);
    }

    @Override
    public <T> T executeAndExtractJsonPath(String query, String jsonPath, HttpHeaders headers) {
        return JsonPath.read(getJsonResult(query, Collections.EMPTY_MAP, headers, null), jsonPath);
    }

    @Override
    public <T> T executeAndExtractJsonPath(String query, String jsonPath, ServletWebRequest servletWebRequest) {
        var httpHeaders = new HttpHeaders();
        servletWebRequest.getHeaderNames().forEachRemaining(name ->
            httpHeaders.addAll(name, List.of(servletWebRequest.getHeaderValues(name)))
        );
        return JsonPath.read(getJsonResult(query, Collections.EMPTY_MAP, httpHeaders, servletWebRequest), jsonPath);
    }

    public <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, Class<T> clazz, HttpHeaders headers) {
        var jsonResult = getJsonResult(query, variables, headers, null);
        try {
            return parseContext.parse(jsonResult).read(jsonPath, clazz);
        }
        catch (MappingException ex) {
            throw new DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, clazz);
        }
    }

    @Override
    public <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Map<String, Object> variables, TypeRef<T> typeRef, HttpHeaders headers) {
        var jsonResult = getJsonResult(query, variables, headers, null);
        try {
            return parseContext.parse(jsonResult).read(jsonPath, typeRef);
        }
        catch (MappingException ex) {
            throw new DgsQueryExecutionDataExtractionException(ex, jsonResult, jsonPath, typeRef);
        }
    }

    @Override
    public DocumentContext executeAndGetDocumentContext(String query, Map<String, Object> variables) {
        return parseContext.parse(getJsonResult(query, variables, null, null));
    }

    @Override
    public DocumentContext executeAndGetDocumentContext(String query, Map<String, Object> variables, HttpHeaders headers) {
        return parseContext.parse(getJsonResult(query, variables, headers, null));
    }

    String getJsonResult(String query, Map<String, Object> variables, HttpHeaders headers, ServletWebRequest servletWebRequest) {
        var executionResult = execute(query, variables, null, headers, null, servletWebRequest);
        if (executionResult.getErrors().size() > 0) {
            throw new QueryException(executionResult.getErrors());
        }
        return Kt.call(() -> objectMapper.writeValueAsString(executionResult.toSpecification()));
    }

    /**
     * Provides the means to identify if executor should reload the [GraphQLSchema] from the given [DgsSchemaProvider].
     * If `true` the schema will be reloaded, else the default schema, provided in the cunstructor of the [DefaultDgsQueryExecutor],
     * will be used.
     *
     * @implSpec The implementation should be thread-safe.
     */
    @FunctionalInterface
    public static interface ReloadSchemaIndicator {
        public boolean reloadSchema();
    }

    private static final Logger logger = LoggerFactory.getLogger(DefaultDgsQueryExecutor.class);

}

