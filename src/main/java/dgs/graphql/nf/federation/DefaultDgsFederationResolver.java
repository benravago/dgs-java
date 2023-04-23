package dgs.graphql.nf.federation;

import com.netflix.graphql.types.errors.TypedGraphQLError; // TODO: replace this

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherResult;
import graphql.execution.ResultPath;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.TypeResolver;

import java.lang.reflect.InvocationTargetException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import dgs.graphql.nf.DgsComponent;
import dgs.graphql.nf.DgsDataFetchingEnvironment;
import dgs.graphql.nf.DgsFederationResolver;
import dgs.graphql.nf.exceptions.InvalidDgsEntityFetcher;
import dgs.graphql.nf.exceptions.MissingDgsEntityFetcherException;
import dgs.graphql.nf.exceptions.MissingFederatedQueryArgument;
import dgs.graphql.nf.internal.EntityFetcherRegistry;
import static nf.graphql.dgs.support.Iterate.*;

import org.dataloader.Try;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Mono;

@DgsComponent
public class DefaultDgsFederationResolver implements DgsFederationResolver {

    /**
     * This constructor is used by DgsSchemaProvider when no custom DgsFederationResolver is provided.
     * This is the most common use case.
     * The default constructor is used to extend the DefaultDgsFederationResolver. In that case injection is used to provide the schemaProvider.
     */
    public DefaultDgsFederationResolver(EntityFetcherRegistry entityFetcherRegistry, Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler) {
        this.setEntityFetcherRegistry(entityFetcherRegistry);
        this.setDgsExceptionHandler(dataFetcherExceptionHandler);
    }

    /**
     * Used when the DefaultDgsFederationResolver is extended.
     */
    @Autowired
    public EntityFetcherRegistry entityFetcherRegistry;

    public EntityFetcherRegistry getEntityFetcherRegistry() {
        return this.entityFetcherRegistry;
    }

    public final void setEntityFetcherRegistry(EntityFetcherRegistry entityFetcherRegistry) {
        this.entityFetcherRegistry = entityFetcherRegistry;
    }

    @Autowired
    public Optional<DataFetcherExceptionHandler> dgsExceptionHandler;

    public Optional<DataFetcherExceptionHandler> getDgsExceptionHandler() {
        return this.dgsExceptionHandler;
    }

    public void setDgsExceptionHandler(Optional<DataFetcherExceptionHandler> optional) {
        this.dgsExceptionHandler = optional;
    }

    @Override
    public DataFetcher<Object> entitiesFetcher() {
        return env -> dgsEntityFetchers(env);
    }

    CompletableFuture<DataFetcherResult<List<Object>>> dgsEntityFetchers(DataFetchingEnvironment env) {
        var resultList = env.<List<Map<String,Object>>>getArgument("representations").stream() // _Entity.argumentName
            .map(values ->
                Try.tryCall(() -> {
                    var typename = values.get("__typename");
                    if (typename == null) {
                        throw new MissingFederatedQueryArgument("__typename");
                    }
                    var fetcher = entityFetcherRegistry.entityFetchers.get(typename.toString());
                    if (fetcher == null) {
                        throw new MissingDgsEntityFetcherException(typename.toString());
                    }
                    var fetcherMethod = fetcher.method();
                    var parameterTypes = fetcherMethod.getParameterTypes();
                    if (!any(parameterTypes, it -> it.isAssignableFrom(Map.class))) {
                        throw new InvalidDgsEntityFetcher("@DgsEntityFetcher " + fetcher.object().getClass().getName() + "." + fetcherMethod.getName() + " is invalid. A DgsEntityFetcher must accept an argument of type Map<String, Object>");
                    }
                    var result = any(parameterTypes, it -> it.isAssignableFrom(DgsDataFetchingEnvironment.class))
                               ? fetcherMethod.invoke(fetcher.object(), values, new DgsDataFetchingEnvironment(env))
                               : fetcherMethod.invoke(fetcher.object(), values);
                    if (result == null) {
                        logger.error("@DgsEntityFetcher returned null for type: " + typename);
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    return switch (result) {
                        case CompletionStage completionStage -> completionStage.toCompletableFuture();
                        case Mono mono -> mono.toFuture();
                        default -> CompletableFuture.completedFuture(result);
                    };
                })
                .map(tryFuture -> Try.tryFuture(tryFuture) )
                .recover(exception -> CompletableFuture.completedFuture(Try.failed(exception)) )
                .get()
            );

        // TODO: missing type information; should be CompletableFuture<Try<Object>>
        var resultFutures = resultList.toList().toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(resultFutures).thenApply(t -> {
            var trySequence = resultList.map(it -> (Try<Object>)it.join());
            return DataFetcherResult.<List<Object>>newResult()
                .data(
                    trySequence
                       .map(tryResult -> tryResult.orElse(null) )
                       .flatMap(r -> r instanceof Collection sequence ? sequence.stream() : Stream.of(r) )
                       .toList()
                )
                .errors(
                    trySequence
                        .filter(tryResult -> tryResult.isFailure())
                        .map(tryResult -> tryResult.getThrowable())
                        .flatMap(e -> {
                            // extract exception from known wrapper types
                            var exception = (e instanceof InvocationTargetException ie && ie.getTargetException() != null) ? ie.getTargetException()
                                          : (e instanceof CompletionException ce && ce.getCause() != null) ? ce.getCause()
                                          : (e);
                            // handle the exception (using the custom handler if present)
                            if (dgsExceptionHandler.isPresent()) {
                                var res = dgsExceptionHandler.get().handleException(
                                    DataFetcherExceptionHandlerParameters
                                        .newExceptionParameters()
                                        .dataFetchingEnvironment(env)
                                        .exception(exception)
                                        .build()
                                );
                                return res.join().getErrors().stream();
                            } else {
                                return Stream.of(
                                    TypedGraphQLError.newInternalErrorBuilder()
                                        .message("%s: %s", exception.getClass().getName(), exception.getMessage())
                                        .path(ResultPath.parse("/_entities"))
                                        .build()
                                );
                            }
                        })
                        .toList()
                )
                .build();
        });
    }

    public Map<Class<?>, String> typeMapping() {
        return Collections.emptyMap();
    }

    @Override
    public TypeResolver typeResolver() {
        return env ->  {
            var src = env.getObject();
            var typeName = typeMapping().containsKey(src.getClass())
                ? typeMapping().get(src.getClass())
                : src.getClass().getSimpleName();
            var type = env.getSchema().getObjectType(typeName);
            if (type == null) {
                logger.warn(
                    "No type definition found for {}. You probably need to provide either a type mapping, " +
                        "or override DefaultDgsFederationResolver.typeResolver(). " +
                        "Alternatively make sure the type name in the schema and your Java model match",
                    src.getClass().getName()
                );
            }
            return type;
        };
    }

    private static final Logger logger = LoggerFactory.getLogger(DefaultDgsFederationResolver.class);

}

