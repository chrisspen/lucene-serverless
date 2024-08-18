package dev.arseny.service;

import dev.arseny.RequestUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoDeletionPolicy;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


@ApplicationScoped
public class IndexWriterService {
    private static final Logger LOG = Logger.getLogger(RequestUtils.class);

    public IndexWriter getIndexWriter(String indexName) {
        int retryCount = 0;
        int maxRetries = 5;
        long retryDelay = 1000; // 1 second

        while (retryCount < maxRetries) {
            try {

                // Delete stale lock files.
                Path lockFile = Paths.get(IndexConstants.LUCENE_INDEX_ROOT_DIRECTORY + indexName, "write.lock");
                if (Files.exists(lockFile)) {
                    // Check if the lock is stale (e.g., older than 5 minutes)
                    long lockAge = Files.getLastModifiedTime(lockFile).toMillis();
                    if (System.currentTimeMillis() - lockAge > 5 * 60 * 1000) {
                        try {
                            Files.delete(lockFile);
                            LOG.info("Deleted stale lock file: " + lockFile.toString());
                        } catch (IOException e) {
                            LOG.error("Failed to delete lock file: " + lockFile.toString(), e);
                        }
                    }
                }

                IndexWriter indexWriter = new IndexWriter(
                        FSDirectory.open(Paths.get(IndexConstants.LUCENE_INDEX_ROOT_DIRECTORY + indexName)),
                        new IndexWriterConfig(new StandardAnalyzer())
                                .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)
                );

                return indexWriter;
            } catch (LockObtainFailedException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    LOG.error("Failed to obtain lock after " + maxRetries + " attempts for index " + indexName, e);
                    throw new RuntimeException("Failed to obtain lock for index " + indexName, e);
                }
                LOG.warn("Lock obtain failed for index " + indexName + ". Retrying in " + retryDelay + "ms...");
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted for index " + indexName, ie);
                }
            } catch (IOException e) {
                LOG.error("Error while trying to create an index writer for index " + indexName, e);
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("Failed to obtain lock for index " + indexName + " after " + maxRetries + " attempts.");
    }
}
