package dgs.graphql.nf.exceptions;

import graphql.execution.ResultPath;
import java.util.Map;

public abstract class DgsException extends RuntimeException {

    public DgsException(String message, Exception cause, ErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType != null ? errorType : ErrorType.UNKNOWN;
    }

    public TypedGraphQLError toGraphQlError(ResultPath path) {
        var error = TypedGraphQLError.newBuilder();
        if (path != null) {
            error.path(path);
        }
        return error
            .errorType(errorType)
            .message(getMessage())
            .extensions(Map.of(EXTENSION_CLASS_KEY, getClass().getName()))
            .build();
    }

    private final ErrorType errorType;

    public static final String EXTENSION_CLASS_KEY = "class";
    
    protected static String override(String m, String o) { return m != null && !m.isBlank() ? m : o; }

}
