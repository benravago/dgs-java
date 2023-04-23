package dgs.graphql.nf.internal;

import java.util.Map;

import org.springframework.http.HttpHeaders;

public interface DgsRequestData {
    public Map<String, Object> extensions();
    public HttpHeaders headers();
}
    
