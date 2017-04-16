package edu.anadolu.exp;

import edu.anadolu.Indexer;
import edu.anadolu.analysis.Analyzers;
import edu.anadolu.cmdline.CmdLineTool;
import edu.anadolu.similarities.MetaTerm;
import org.apache.lucene.benchmark.byTask.feeds.DocData;
import org.apache.lucene.benchmark.byTask.feeds.NoMoreDataException;
import org.apache.lucene.benchmark.byTask.feeds.TrecContentSource;
import org.apache.lucene.benchmark.byTask.feeds.TrecParserByPath;
import org.apache.lucene.benchmark.byTask.utils.Config;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Helper class for Robust Track 2004
 * http://trec.nist.gov/data/robust/04.guidelines.html
 */
public class ROB04 {

    public static int index(String dataDir, Path indexPath) throws IOException {


        final Directory dir = FSDirectory.open(indexPath);

        final IndexWriterConfig iwc = new IndexWriterConfig(Analyzers.analyzer());

        iwc.setSimilarity(new MetaTerm());
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(512.0);
        iwc.setUseCompoundFile(false);
        iwc.setMergeScheduler(new ConcurrentMergeScheduler());

        final IndexWriter writer = new IndexWriter(dir, iwc);

        TrecContentSource tcs = new TrecContentSource();
        Properties props = new Properties();
        props.setProperty("print.props", "false");
        props.setProperty("content.source.verbose", "false");
        props.setProperty("content.source.excludeIteration", "true");
        props.setProperty("docs.dir", dataDir);
        props.setProperty("trec.doc.parser", TrecParserByPath.class.getName());
        props.setProperty("content.source.forever", "false");
        tcs.setConfig(new Config(props));

        tcs.resetInputs();

        DocData dd = new DocData();
        while (true) {

            try {
                dd = tcs.getNextDocData(dd);
            } catch (NoMoreDataException no) {
                break;
            }


            String id = dd.getName();
            String contents = dd.getBody();

            // don't index empty documents
            if (contents == null || contents.trim().length() == 0) {
                System.err.println(id + " " + dd.getTitle());
                continue;
            }

            // make a new, empty document
            Document document = new Document();

            // document ID
            document.add(new StringField(Indexer.FIELD_ID, id, Field.Store.YES));

            // entire document
            document.add(new Indexer.NoPositionsTextField(Indexer.FIELD_CONTENTS, contents));

            writer.addDocument(document);

        }

        tcs.close();

        final int numIndexed = writer.maxDoc();

        try {
            writer.commit();
            writer.forceMerge(1);
        } finally {
            writer.close();
        }

        dir.close();

        return numIndexed;
    }

    public static void main(String[] args) throws IOException {

        String dataDir = "/Users/iorixxx/TREC_VOL4/";

        Path indexPath = Paths.get("/tmp" +
                "/ROBIndex/");

        System.out.println("Indexing to directory '" + indexPath.toAbsolutePath() + "'...");
        final long start = System.nanoTime();
        final int numIndexed = index(dataDir, indexPath);
        System.out.println("Total " + numIndexed + " documents indexed in " + CmdLineTool.execution(start));

    }
}
