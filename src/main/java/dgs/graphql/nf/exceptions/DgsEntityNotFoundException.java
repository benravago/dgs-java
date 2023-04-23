package dgs.graphql.nf.exceptions;

public class DgsEntityNotFoundException extends DgsException {

    public DgsEntityNotFoundException(String message) {
        super(override(message,"Requested entity not found"), null, ErrorType.NOT_FOUND);
    }

}

