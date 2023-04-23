package dgs.graphql.nf.internal;

import java.util.function.BiFunction;

import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.WebRequest;

public interface DgsQueryExecutorRequestCustomizer extends BiFunction<WebRequest, HttpHeaders, WebRequest> {

    @Override
    WebRequest apply(WebRequest request, HttpHeaders headers);

    static DgsQueryExecutorRequestCustomizer DEFAULT_REQUEST_CUSTOMIZER() { return (request, headers) -> request; }

}
