package dgs.graphql.nf.exceptions;

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDataFetcherExceptionHandler implements DataFetcherExceptionHandler {

    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException( DataFetcherExceptionHandlerParameters handlerParameters) {
        return CompletableFuture.completedFuture(this.doHandleException(handlerParameters));
    }

    DataFetcherExceptionHandlerResult doHandleException(DataFetcherExceptionHandlerParameters handlerParameters) {
        var exception = unwrapCompletionException(handlerParameters.getException());
        logger.error(
            "Exception while executing data fetcher for " + handlerParameters.getPath() + ": " + exception.getMessage(),
            exception
        );

        var graphqlError = switch (exception) {
            case DgsException dgsException -> dgsException.toGraphQlError(handlerParameters.getPath());
            default -> ( isSpringSecurityAccessException(exception)
                ? TypedGraphQLError.newPermissionDeniedBuilder() : TypedGraphQLError.newInternalErrorBuilder())
                    .message("%s: %s", exception.getClass().getName(), exception.getMessage())
                    .path(handlerParameters.getPath())
                    .build();
        };

        return DataFetcherExceptionHandlerResult.newResult()
            .error(graphqlError)
            .build();
    }

    Throwable unwrapCompletionException(Throwable e) {
        return (e instanceof CompletionException ce && ce.getCause() != null) ? ce.getCause() : e;
    }

    private static final Logger logger = LoggerFactory.getLogger(DefaultDataFetcherExceptionHandler.class);

    private static Class<?> accessDeniedException;

    boolean isSpringSecurityAccessException(Throwable exception) {
        if (accessDeniedException == null) {
            try {
                accessDeniedException = Class.forName("org.springframework.security.access.AccessDeniedException");
            } catch (Exception e) {
                accessDeniedException = Void.TYPE;
                logger.trace("Unable to verify if {} is a Spring Security's AccessDeniedException.", exception, e);
            }
        }
        return accessDeniedException.isInstance(exception);
    }

}

