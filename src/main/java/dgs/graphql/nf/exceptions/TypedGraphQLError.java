package dgs.graphql.nf.exceptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static graphql.Assert.assertNotNull;

public class TypedGraphQLError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> locations;
    private final ErrorClassification classification;
    private final List<Object> path;
    private final Map<String, Object> extensions;

    @JsonCreator
    public TypedGraphQLError(@JsonProperty("message") String message,
                             @JsonProperty("locations") List<SourceLocation> locations,
                             @JsonProperty("classification") ErrorClassification classification,
                             @JsonProperty("path") List<Object> path,
                             @JsonProperty("extensions") Map<String, Object> extensions) {
        this.message = message;
        this.locations = locations;
        this.classification = classification;
        this.path = path;
        this.extensions = extensions;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    // We return null here because we don't want graphql-java to write classification field
    public ErrorClassification getErrorType() {
        return null;
    }

    @Override
    public List<Object> getPath() {
        return path;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * Create new Builder instance to customize error.
     *
     * @return A new TypedGraphQLError.Builder instance to further customize the error.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.INTERNAL.
     */
    public static Builder newInternalErrorBuilder() {
        return new Builder().errorType(ErrorType.INTERNAL);
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.NOT_FOUND.
     */
    public static Builder newNotFoundBuilder() {
        return new Builder().errorType(ErrorType.NOT_FOUND);
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.PERMISSION_DENIED.
     */
    public static Builder newPermissionDeniedBuilder() {
        return new Builder().errorType(ErrorType.PERMISSION_DENIED);
    }

    /**
     * Create new Builder instance to customize error.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets ErrorType.BAD_REQUEST.
     */
    public static Builder newBadRequestBuilder() {
        return new Builder().errorType(ErrorType.BAD_REQUEST);
    }

    /**
     * Create new Builder instance to further customize an error that results in a {@link ErrorDetail.Common#CONFLICT conflict}.
     * @return A new TypedGraphQLError.Builder instance to further customize the error. Pre-sets {@link ErrorDetail.Common#CONFLICT}.
     */
    public static Builder newConflictBuilder() {
        return new Builder().errorDetail(ErrorDetail.Common.CONFLICT);
    }

    @Override
    public String toString() {
        return "TypedGraphQLError{" +
                "message='" + message + '\'' +
                ", locations=" + locations +
                ", path=" + path +
                ", extensions=" + extensions +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != this.getClass()) return false;

        TypedGraphQLError e = (TypedGraphQLError)obj;

        if (!Objects.equals(message, e.message)) return false;
        if (!Objects.equals(locations, e.locations)) return false;
        if (!Objects.equals(path, e.path)) return false;
        if (!Objects.equals(extensions, e.extensions)) return false;

        return true;
    }

    public static class Builder {
        private String message;
        private List<Object> path;
        private List<SourceLocation> locations = new ArrayList<>();
        private ErrorClassification errorClassification = ErrorType.UNKNOWN;
        private Map<String, Object> extensions;
        private String origin;
        private String debugUri;
        private Map<String, Object> debugInfo;

        private Builder() {
        }

        private String defaultMessage() {
            return errorClassification.toString();
        }

        private Map<String, Object> getExtensions() {
            HashMap<String, Object> extensionsMap = new HashMap<>();
            if (extensions != null) extensionsMap.putAll(extensions);
            if (errorClassification instanceof ErrorType) {
                extensionsMap.put("errorType", String.valueOf(errorClassification));
            } else if (errorClassification instanceof ErrorDetail) {
                extensionsMap.put("errorType", String.valueOf(((ErrorDetail) errorClassification).getErrorType()));
                extensionsMap.put("errorDetail", String.valueOf(errorClassification));
            }
            if (origin != null) extensionsMap.put("origin", origin);
            if (debugUri != null) extensionsMap.put("debugUri", debugUri);
            if (debugInfo != null) extensionsMap.put("debugInfo", debugInfo);
            return extensionsMap;
        }

        public Builder message(String message, Object... formatArgs) {
            this.message = String.format(assertNotNull(message), formatArgs);
            return this;
        }

        public Builder locations(List<SourceLocation> locations) {
            this.locations.addAll(assertNotNull(locations));
            return this;
        }

        public Builder location(SourceLocation location) {
            this.locations.add(assertNotNull(location));
            return this;
        }

        public Builder path(ResultPath path) {
            this.path = assertNotNull(path).toList();
            return this;
        }

        public Builder path(List<Object> path) {
            this.path = assertNotNull(path);
            return this;
        }

        public Builder errorType(ErrorType errorType) {
            this.errorClassification = assertNotNull(errorType);
            return this;
        }

        public Builder errorDetail(ErrorDetail errorDetail) {
            this.errorClassification = assertNotNull(errorDetail);
            return this;
        }

        public Builder origin(String origin) {
            this.origin = assertNotNull(origin);
            return this;
        }

        public Builder debugUri(String debugUri) {
            this.debugUri = assertNotNull(debugUri);
            return this;
        }

        public Builder debugInfo(Map<String, Object> debugInfo) {
            this.debugInfo = assertNotNull(debugInfo);
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = assertNotNull(extensions);
            return this;
        }

        /**
         * @return a newly built GraphQLError
         */
        public TypedGraphQLError build() {
            if (message == null) message = defaultMessage();
            return new TypedGraphQLError(message, locations, errorClassification, path, getExtensions());
        }
    }

}
