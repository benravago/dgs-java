package dgs.graphql.nf.exceptions;

import graphql.GraphQLError;
import java.util.List;

public class QueryException extends RuntimeException {
    
    public QueryException(List<? extends GraphQLError> errors) {
        super(errors.toString());
    }

}

