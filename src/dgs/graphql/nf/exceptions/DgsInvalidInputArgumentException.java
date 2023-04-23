package dgs.graphql.nf.exceptions;

public class DgsInvalidInputArgumentException extends DgsException {
    
    public DgsInvalidInputArgumentException(String message, Exception cause) {
        super(message, cause, ErrorType.BAD_REQUEST);
    }

}

