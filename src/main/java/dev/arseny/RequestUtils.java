package dev.arseny;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.arseny.model.*;
import org.jboss.logging.Logger;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RequestUtils {
    private static final Logger LOG = Logger.getLogger(RequestUtils.class);

    static ObjectWriter writer = new ObjectMapper().writerFor(ErrorResponse.class);
    static ObjectWriter queryResponseWriter = new ObjectMapper().writerFor(QueryResponse.class);
    static ObjectReader indexRequestReader = new ObjectMapper().readerFor(IndexRequest.class);
    static ObjectReader deleteIndexRequestReader = new ObjectMapper().readerFor(DeleteIndexRequest.class);
    static ObjectReader queryRequestReader = new ObjectMapper().readerFor(QueryRequest.class);

    // Cache for allowed origins
    private static List<String> cachedAllowedOriginsList;

    // Converts the ALLOWED_ORIGINS env string to a list of domain strings.
    public static List<String> getAllowedOriginsList() {
        // Check if the cache is empty
        if (cachedAllowedOriginsList == null) {
            // Retrieve the allowedOrigins from the environment variable
            String allowedOrigins = System.getenv("ALLOWED_ORIGINS") != null ? System.getenv("ALLOWED_ORIGINS") : "";

            // Split the allowedOrigins by comma into a list, and trim spaces
            cachedAllowedOriginsList = Arrays.stream(allowedOrigins.split(","))
                                              .map(String::trim)
                                              .collect(Collectors.toList());
        }

        return cachedAllowedOriginsList;
    }

    // Return true if the given origin matches ALLOW_ORIGINS.
    public static boolean isAllowedOrigin(String origin) {
        List<String> allowedOriginsList = getAllowedOriginsList();
        if (origin != null && allowedOriginsList.contains(origin)) {
            return true;
        }
        return false;
    }

    // Returns the value that should be used in the Access-Control-Allow-Origin CORS header.
    public static String getAllowedOriginsHeaderValue(String origin) {
        List<String> allowedOriginsList = getAllowedOriginsList();
        if (origin != null && allowedOriginsList.contains(origin)) {
            return origin;
        }
        if(!allowedOriginsList.isEmpty()){
            return allowedOriginsList.get(0);
        }
        return "*";
    }

    public static Map<String, String> getCORSHeaders(String origin) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", getAllowedOriginsHeaderValue(origin));
        headers.put("Access-Control-Allow-Credentials", "true");
        headers.put("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS, HEAD");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Accept, X-Requested-With");
        return headers;
    }

    public static APIGatewayProxyResponseEvent errorResponse(int errorCode, String message) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            return response.withStatusCode(errorCode).withBody(writer.writeValueAsString(new ErrorResponse(message, errorCode)));
        } catch (JsonProcessingException e) {
            LOG.error(e);
            return response.withStatusCode(500).withBody("Internal error");
        }
    }

    public static IndexRequest parseIndexRequest(String eventBody) {
        try {
            return indexRequestReader.readValue(eventBody);
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse list of Index Requests in body", e);
        }
    }

    public static DeleteIndexRequest parseDeleteIndexRequest(APIGatewayProxyRequestEvent event) {
        try {
            return deleteIndexRequestReader.readValue(event.getBody());
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse a delete index request in body", e);
        }
    }

    public static QueryRequest parseQueryRequest(APIGatewayProxyRequestEvent event) {
        try {
            LOG.info("parseQueryRequest: Received body: " + event.getBody());
            Map<String, String> headers = event.getHeaders();
            LOG.info("parseQueryRequest: Received headers: " + headers);
            return queryRequestReader.readValue(event.getBody());
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse a query request in body", e);
        }
    }

    public static APIGatewayProxyResponseEvent successResponse(QueryResponse queryResponse) throws JsonProcessingException {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            return response.withStatusCode(200).withBody(queryResponseWriter.writeValueAsString(queryResponse));
        } catch (JsonProcessingException e) {
            LOG.error(e);
            return response.withStatusCode(500).withBody("Internal error");
        }
    }
}
