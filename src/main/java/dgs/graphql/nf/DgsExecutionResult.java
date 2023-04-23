package dgs.graphql.nf;

import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

import java.util.List;
import java.util.Map;

import dgs.graphql.nf.internal.utils.TimeTracer;
import dgs.graphql.nf.support.Kt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class DgsExecutionResult implements ExecutionResult {

    public DgsExecutionResult( ExecutionResult executionResult,  HttpHeaders headers,  HttpStatus status) {
        this.executionResult = executionResult;
        this.headers = headers;
        this.status = status;
        this.addExtensionsHeaderKeyToHeader();
    }

    private final ExecutionResult executionResult;
    private HttpHeaders headers;
    private final HttpStatus status;

    public HttpStatus getStatus() {
        return this.status;
    }

    @Override
    public <T> T getData() { return (T)executionResult.getData(); }
    @Override
    public List<GraphQLError> getErrors() { return executionResult.getErrors(); }
    @Override
    public Map<Object, Object> getExtensions() { return executionResult.getExtensions(); }
    @Override
    public boolean isDataPresent() { return executionResult.isDataPresent(); }

    /** Read-Only reference to the HTTP Headers. */
    public HttpHeaders headers() {
        return HttpHeaders.readOnlyHttpHeaders((HttpHeaders)this.headers);
    }

    public ResponseEntity<Object> toSpringResponse(ObjectMapper mapper) {
        byte[] result;
        try {
            result = TimeTracer.INSTANCE.logTime(
                () -> Kt.call(() -> mapper.writeValueAsBytes(toSpecification())),
                logger,
                "Serialized JSON result in {}ms"
            );
        } catch (Exception ex) {
            var errorMessage = "Error serializing response: " + ex.getMessage();
            var errorResponse = new ExecutionResultImpl(GraphqlErrorBuilder.newError().message(errorMessage, new Object[0]).build());
            logger.error(errorMessage, (Throwable)ex);
            result = Kt.call(() -> mapper.writeValueAsBytes(errorResponse.toSpecification()));
        }
        return new ResponseEntity(
            result,
            headers,
            status
        );
    }


    // Refer to https://github.com/Netflix/dgs-framework/pull/1261 for further details.
    public Map<String, Object> toSpecification() {
        var spec = executionResult.toSpecification();
        if (spec.get("extensions") != null && getExtensions().containsKey(DGS_RESPONSE_HEADERS_KEY)) {
            var extensions = (Map) spec.get("extensions");
            if (extensions.size() != 1) {
                extensions.remove(DGS_RESPONSE_HEADERS_KEY);
                spec.put("extensions", extensions); // is it ok to update/reuse ??
            } else {
                spec.remove("extensions");
            }
        }
        return spec;
    }


    // Refer to https://github.com/Netflix/dgs-framework/pull/1261 for further details.
    private void addExtensionsHeaderKeyToHeader() {
        var extensions = executionResult.getExtensions();
        if (extensions.containsKey(DGS_RESPONSE_HEADERS_KEY)) {
            var object = extensions.get(DGS_RESPONSE_HEADERS_KEY);
            if (object instanceof Map dgsResponseHeaders && !dgsResponseHeaders.isEmpty()) {
                // If the HttpHeaders are empty/read-only we need to switch to a new instance that allows us
                // to store the headers that are part of the GraphQL response _extensions_.
                if (headers == HttpHeaders.EMPTY) {
                    headers = new HttpHeaders();
                }
                dgsResponseHeaders.forEach((k,v) -> {
                    if (k != null) {
                        headers.add(k.toString(), v != null ? v.toString() : null);
                    }
                });
            } else {
                logger.warn(
                    "{} must be of type java.util.Map, but was {}",
                    DGS_RESPONSE_HEADERS_KEY,
                    object
                );
            }
        }
    }

    /**
     * Facilitate the construction of a [DgsExecutionResult] instance.
     */
    public static class Builder {

        private ExecutionResult executionResult = DEFAULT_EXECUTION_RESULT;

        public ExecutionResult getExecutionResult() {
            return this.executionResult;
        }

        public Builder executionResult(ExecutionResult executionResult) {
            this.executionResult = executionResult;
            return this;
        }

        public Builder executionResult(ExecutionResultImpl.Builder executionResultBuilder) {
            this.executionResult = executionResultBuilder.build();
            return this;
        }

        private HttpHeaders headers = HttpHeaders.EMPTY;

        public HttpHeaders getHeaders() {
            return this.headers;
        }

        public Builder headers( HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        private HttpStatus status = HttpStatus.OK;

        public HttpStatus getStatus() {
            return this.status;
        }

        public Builder status(HttpStatus status) {
            this.status = status;
            return this;
        }

        private static final ExecutionResultImpl DEFAULT_EXECUTION_RESULT = ExecutionResultImpl.newExecutionResult().build();

        public DgsExecutionResult build() {
            if (executionResult == null) throw new IllegalStateException("Required value was null.");
            return new DgsExecutionResult(executionResult, headers, status);
        }

    }

    // defined in here and DgsRestController, for backwards compatibility. Keep these two variables synced.
    public static final String DGS_RESPONSE_HEADERS_KEY = "dgs-response-headers";

    private static final Logger logger = LoggerFactory.getLogger(DgsExecutionResult.class);

    public static Builder builder() {
        return new Builder();
    }

}
