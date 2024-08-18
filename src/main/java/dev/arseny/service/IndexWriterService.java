package dev.arseny.service;

import dev.arseny.RequestUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoDeletionPolicy;
import org.apache.lucene.store.FSDirectory;
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
        try {

            // Define the path to the lock file
            Path lockFile = Paths.get(IndexConstants.LUCENE_INDEX_ROOT_DIRECTORY + indexName, "write.lock");

            // Check if the lock file exists and delete it if it does
            if (Files.exists(lockFile)) {
                try {
                    Files.delete(lockFile);
                    LOG.info("Deleted stale lock file: " + lockFile.toString());
                } catch (IOException e) {
                    LOG.error("Failed to delete lock file: " + lockFile.toString(), e);
                }
            }

            IndexWriter indexWriter = new IndexWriter(
                    FSDirectory.open(Paths.get(IndexConstants.LUCENE_INDEX_ROOT_DIRECTORY + indexName)),
                    new IndexWriterConfig(new StandardAnalyzer())
                            .setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)
            );

            return indexWriter;
        } catch (IOException e) {
            LOG.error("Error while trying to create an index writer for index " + indexName, e);
            throw new RuntimeException(e);
        }
    }
}
