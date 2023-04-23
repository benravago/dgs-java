package dgs.graphql.nf.exceptions;

public final class NoDgsFederationResolverFoundException extends RuntimeException {

    public NoDgsFederationResolverFoundException() {
        super("@key directive was used in schema, but could not find DgsComponent implementing DgsFederationResolver.");
    }

}

