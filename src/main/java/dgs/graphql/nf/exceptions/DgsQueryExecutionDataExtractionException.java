package dgs.graphql.nf.exceptions;

import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingException;

public class DgsQueryExecutionDataExtractionException extends RuntimeException {

    public DgsQueryExecutionDataExtractionException(Exception ex, String jsonResult, String jsonPath, String targetClass) {
        super(message(jsonResult,jsonPath,targetClass),ex);
    }

    private static final String message(String jsonResult,  String jsonPath,  String targetClass) {
        return String.format("Error deserializing data from '%s' with JsonPath '%s' and target class %s", jsonResult, jsonPath, targetClass);
    }

    public DgsQueryExecutionDataExtractionException(MappingException ex, String jsonResult, String jsonPath, TypeRef<?> targetClass) {
        this(ex, jsonResult, jsonPath, targetClass.getType().getTypeName());
    }

    public DgsQueryExecutionDataExtractionException(MappingException ex, String jsonResult, String jsonPath, Class<?> targetClass) {
        this(ex, jsonResult, jsonPath, targetClass.getName());
    }

}
