package dgs.graphql.nf.context;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.WebRequest;

/**
 * When a bean implementing this interface is found, the framework will call the [build] method for every request.
 * The result of the [build] method is placed on the [DgsContext] and can be retrieved with [DgsContext.customContext]
 * or with one of the static methods on [DgsContext] given a DataFetchingEnvironment or batchLoaderEnvironment.
 */
public interface DgsCustomContextBuilderWithRequest<T> {
    public T build( Map<String,Object> extensions,  HttpHeaders headers,  WebRequest webRequest);
}

