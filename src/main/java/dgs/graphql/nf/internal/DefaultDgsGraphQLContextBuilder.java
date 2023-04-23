package dgs.graphql.nf.internal;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import dgs.graphql.nf.context.DgsContext;
import dgs.graphql.nf.context.DgsCustomContextBuilder;
import dgs.graphql.nf.context.DgsCustomContextBuilderWithRequest;
import dgs.graphql.nf.internal.utils.TimeTracer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.WebRequest;

public class DefaultDgsGraphQLContextBuilder {

    private final Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder;
    private final Optional<DgsCustomContextBuilderWithRequest<?>> dgsCustomContextBuilderWithRequest;

    public DefaultDgsGraphQLContextBuilder(Optional<DgsCustomContextBuilder<?>> dgsCustomContextBuilder, Optional<DgsCustomContextBuilderWithRequest<?>> dgsCustomContextBuilderWithRequest) {
        this.dgsCustomContextBuilder = dgsCustomContextBuilder;
        this.dgsCustomContextBuilderWithRequest = dgsCustomContextBuilderWithRequest;
    }

    public DgsContext build(DgsWebMvcRequestData dgsRequestData) {
        return TimeTracer.INSTANCE.logTime(() -> buildDgsContext(dgsRequestData), logger, "Created DGS context in {}ms");
    }

    DgsContext buildDgsContext(DgsWebMvcRequestData dgsRequestData) {
        var customContext =
            dgsCustomContextBuilderWithRequest.isPresent() ?
                dgsCustomContextBuilderWithRequest.get().build(
                    (dgsRequestData.extensions() != null ? dgsRequestData.extensions() : Collections.EMPTY_MAP),
                    HttpHeaders.readOnlyHttpHeaders(dgsRequestData.headers() != null ? dgsRequestData.headers() : new HttpHeaders()),
                    dgsRequestData.webRequest()
                )
            : dgsCustomContextBuilder.isPresent() ? dgsCustomContextBuilder.get().build()
            : dgsRequestData; // This is for backwards compatibility - we previously made DefaultRequestData the custom context if no custom context was provided.

        return new DgsContext(
            customContext,
            dgsRequestData
        );
    }

    private static Logger logger = LoggerFactory.getLogger(DefaultDgsGraphQLContextBuilder.class);

    public record DgsWebMvcRequestData(
        Map<String, Object> extensions,
        HttpHeaders headers,
        WebRequest webRequest
    )
    implements DgsRequestData {}

}
