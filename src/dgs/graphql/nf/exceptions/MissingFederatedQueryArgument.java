package dgs.graphql.nf.exceptions;

import java.util.Arrays;

public class MissingFederatedQueryArgument extends DgsBadRequestException {

    public MissingFederatedQueryArgument(String ... fields) {
        super("The federated query is missing field(s) " + Arrays.toString(fields));
    }

}

