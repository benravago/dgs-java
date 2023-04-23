package dgs.graphql.nf.exceptions;

public class DgsMissingCookieException extends RuntimeException {

    public DgsMissingCookieException(String cookieName) {
        super("Required cookie '" + cookieName + "' was not provided");
    }

}

