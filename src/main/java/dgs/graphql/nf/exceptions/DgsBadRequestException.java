package dgs.graphql.nf.exceptions;

public class DgsBadRequestException extends DgsException {

    public DgsBadRequestException(String message) {
        super(override(message, "Malformed or incorrect request"), null, ErrorType.BAD_REQUEST);
    }

    public static DgsBadRequestException NULL_OR_EMPTY_QUERY_EXCEPTION = new DgsBadRequestException("GraphQL operations must contain a non-empty `query`.");

}

