package dgs.graphql.nf.internal;

import dgs.graphql.nf.DgsDataFetchingEnvironment;
import dgs.graphql.nf.context.ReactiveDgsContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

public interface ReactiveDataFetcherResultProcessor {

    class MonoDataFetcherResultProcessor implements DataFetcherResultProcessor {
        @Override
        public boolean supportsType(Object originalResult) {
            return originalResult instanceof Mono;
        }
        @Override
        public Object process(Object originalResult, DgsDataFetchingEnvironment dfe) {
            if (originalResult instanceof Mono monoResult) {
                return monoResult.contextWrite(reactorContextFrom(dfe)).toFuture();
            } else {
                throw new IllegalArgumentException("Instance passed to " + getClass().getName() + " was not a Mono<*>. It was a " + originalResult.getClass().getName() + " instead");
            }
        }
    }

    class FluxDataFetcherResultProcessor implements DataFetcherResultProcessor {
        @Override
        public boolean supportsType(Object originalResult) {
            return originalResult instanceof Flux;
        }
        @Override
        public Object process(Object originalResult, DgsDataFetchingEnvironment dfe) {
            if (originalResult instanceof Flux fluxResult) {
                return fluxResult.contextWrite(reactorContextFrom(dfe)).collectList().toFuture();
            }
            throw new IllegalArgumentException("Instance passed to " + getClass().getName() + " was not a Flux<*>. It was a " + originalResult.getClass().getName() + " instead");
        }
    }

    static ContextView reactorContextFrom(DgsDataFetchingEnvironment dfe) {
        var reactiveDgsContext = ReactiveDgsContext.from(dfe).getReactorContext();
        return reactiveDgsContext != null ? reactiveDgsContext : Context.empty();
    }

}
