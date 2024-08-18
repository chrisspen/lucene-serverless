package dev.arseny.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import dev.arseny.RequestUtils;
import dev.arseny.model.IndexRequest;
import dev.arseny.service.IndexWriterService;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named("index")
public class IndexHandler implements RequestHandler<SQSEvent, APIGatewayProxyResponseEvent> {
    private static final Logger LOG = Logger.getLogger(IndexHandler.class);

    @Inject
    protected IndexWriterService indexWriterService;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(SQSEvent event, Context context) {
        List<SQSEvent.SQSMessage> records = event.getRecords();

        List<IndexRequest> requests = new ArrayList<>();

        for (SQSEvent.SQSMessage record : records) {
            requests.add(RequestUtils.parseIndexRequest(record.getBody()));
        }

        indexDocuments(requests);

        return new APIGatewayProxyResponseEvent().withStatusCode(200);
    }

    private void indexDocuments(List<IndexRequest> requests) {

        Map<String, IndexWriter> writerMap = new HashMap<>(); // {indexName: writer}

        for (IndexRequest request : requests) {
            IndexWriter writer;
            if (writerMap.containsKey(request.getIndexName())) {
                writer = writerMap.get(request.getIndexName());
            } else {
                writer = indexWriterService.getIndexWriter(request.getIndexName());
                writerMap.put(request.getIndexName(), writer);
            }

            List<Document> documents = new ArrayList<>();
            List<Term> termsToDelete = new ArrayList<>();

            for (Map<String, Object> requestDocument : request.getDocuments()) {
                if (Boolean.TRUE.equals(requestDocument.get("deleted")) && requestDocument.containsKey("uuid")) {
                    Term term = new Term("uuid", requestDocument.get("uuid").toString());
                    termsToDelete.add(term);
                    LOG.info("Scheduled document for deletion: " + term.toString());
                } else {
                    Document document = new Document();
                    for (Map.Entry<String, Object> entry : requestDocument.entrySet()) {

                        // Skip missing or invalid key/value pairs.
                        // Without this, if someone passes a document via an ARN call like {'slug': null}
                        // then the document.add() below will blow up since nulls aren't allowed.
                        if (entry.getKey() == null || entry.getValue() == null) {
                            LOG.warn("Encountered null key or value in document entry: " + entry);
                            continue;
                        }

                        if ("uuid".equals(entry.getKey())) {
                            // UUIDs must be Strings so they can be exactly matched for deletion by Term later on.
                            FieldType fieldType = new FieldType();
                            fieldType.setTokenized(false);
                            fieldType.setStored(true);
                            fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
                            Field idField = new Field(entry.getKey(), entry.getValue().toString(), fieldType);
                            document.add(idField);
                        } else {
                            document.add(new TextField(entry.getKey(), entry.getValue().toString(), Field.Store.YES));
                        }
                    }
                    documents.add(document);
                }
            }

            try {
                if (!termsToDelete.isEmpty()) {
                    writer.deleteDocuments(termsToDelete.toArray(new Term[0]));
                    LOG.info("Deleted documents matching terms: " + termsToDelete);
                } else {
                    LOG.info("Nothing to delete.");
                }
                if (!documents.isEmpty()) {
                    writer.addDocuments(documents);
                    LOG.info("Index successfully updated for " + request.getIndexName());
                } else {
                    LOG.info("Nothing to add.");
                }
            } catch (IOException e) {
                LOG.error("Error updating index for " + request.getIndexName(), e);
            }
        }

        commitChanges(writerMap);
        closeWriters(writerMap);

    }

    private void commitChanges(Map<String, IndexWriter> writerMap) {
        for (IndexWriter writer : writerMap.values()) {
            try {
                writer.commit();
            } catch (IOException e) {
                LOG.error("Error committing IndexWriter", e);
            }
        }
    }

    private void closeWriters(Map<String, IndexWriter> writerMap) {
        for (IndexWriter writer : writerMap.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                LOG.error("Error closing IndexWriter", e);
            }
        }
    }

}
