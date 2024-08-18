package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import dev.arseny.RequestUtils;
import dev.arseny.model.QueryRequest;
import dev.arseny.model.QueryResponse;
import dev.arseny.service.IndexSearcherService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

@Named("query")
public class QueryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger LOG = Logger.getLogger(QueryHandler.class);

    @Inject
    protected IndexSearcherService indexSearcherService;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        // Do some simple CORS access checking. Not super secure but it's better than nothing.
        String allowedDomains = System.getenv("ALLOWED_DOMAINS");
        List<String> allowedDomainList = Arrays.asList(allowedDomains.split(","));
        Map<String, String> headers = event.getHeaders();
        String referer = headers.get("Referer");
        if (!isAllowedDomain(referer, allowedDomainList)) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(403)
                    .withBody("Forbidden: Access is denied");
        }

        QueryRequest queryRequest = RequestUtils.parseQueryRequest(event);

        QueryParser qp = new QueryParser("content", new StandardAnalyzer());

        QueryResponse queryResponse = new QueryResponse();

        try {
            Query query = qp.parse(queryRequest.getQuery());

            IndexSearcher searcher = indexSearcherService.getIndexSearcher(queryRequest.getIndexName());

            TopDocs topDocs = searcher.search(query, 10);

            for (ScoreDoc scoreDocs : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDocs.doc);

                Map<String, String> result = new HashMap<>();

                for (IndexableField field : document.getFields()) {
                    result.put(field.name(), field.stringValue());
                }

                queryResponse.getDocuments().add(result);
            }

            queryResponse.setTotalDocuments((topDocs.totalHits.relation == TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO ? "≥" : "") + topDocs.totalHits.value);

            return RequestUtils.successResponse(queryResponse);
        } catch (ParseException | IOException e) {
            LOG.error(e);

            return RequestUtils.errorResponse(500, "Error");
        }
    }

    private boolean isAllowedDomain(String referer, List<String> allowedDomains) {
        // If the allowedDomains list is empty, allow all requests
        if (allowedDomains.isEmpty() || (allowedDomains.size() == 1 && allowedDomains.get(0).isEmpty())) {
            return true;
        }

        if (referer == null) {
            return false;
        }

        try {
            URI uri = new URI(referer);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            // Check if the host (domain name) is in the allowed domains list
            return allowedDomains.contains(host);
        } catch (URISyntaxException e) {
            // Handle invalid URI syntax in the Referer header
            return false;
        }
    }

}
