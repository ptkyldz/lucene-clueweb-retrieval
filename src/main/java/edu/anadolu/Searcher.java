package edu.anadolu;

import edu.anadolu.analysis.Analyzers;
import edu.anadolu.analysis.Tag;
import edu.anadolu.datasets.DataSet;
import edu.anadolu.similarities.MATF;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.ModelBase;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.clueweb09.InfoNeed;
import org.clueweb09.tracks.Track;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static edu.anadolu.Indexer.FIELD_CONTENTS;
import static edu.anadolu.Indexer.FIELD_ID;

/**
 * Searcher for ClueWeb09
 * 200 Topics from TREC 2009-1012 Web Track
 */
public class Searcher implements Closeable {

    protected final IndexReader reader;

    private static final String WEIGHT_CONTENTS = "weight(contents:";

    private static final String FREQ = "freq=";

    private static final String DOC_LEN = "docLen=";

    protected final String indexTag;

    private final class SearcherThread extends Thread {

        private final QueryParser.Operator operator;
        private final Track track;
        private final Similarity similarity;
        private final Path path;

        public SearcherThread(Track track, Similarity similarity, QueryParser.Operator operator, Path path) {
            this.operator = operator;
            this.track = track;
            this.similarity = similarity;
            this.path = path;
            setName(indexTag + similarity.toString() + track.toString());
        }

        @Override
        public void run() {
            try {
                search(track, similarity, operator, path);
            } catch (Exception e) {
                System.out.println(Thread.currentThread().getName() + ": unexpected exception : " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public String toString() {
            return indexTag + similarity.toString() + track.toString();
        }


    }

    protected final DataSet dataSet;
    protected final int numHits;

    protected final Tag analyzerTag;

    public Searcher(Path indexPath, DataSet dataSet, int numHits) throws IOException {

        if (!Files.exists(indexPath) || !Files.isDirectory(indexPath) || !Files.isReadable(indexPath)) {
            throw new IllegalArgumentException(indexPath + " does not exist or is not a directory.");
        }

        this.reader = DirectoryReader.open(FSDirectory.open(indexPath));
        this.indexTag = indexPath.getFileName().toString();
        this.dataSet = dataSet;
        this.numHits = numHits;

        this.analyzerTag = Tag.tag(indexTag);

        System.out.println("Opened index directory : " + indexPath + " has " + reader.numDocs() + " numDocs and has " + reader.maxDoc() + " maxDocs");
        System.out.println("Analyzer Tag : " + analyzerTag);

    }

    public String toString(Similarity similarity, QueryParser.Operator operator, int part) {
        String p = part == 0 ? "all" : Integer.toString(part);
        return similarity.toString().replaceAll(" ", "_") + "_" + FIELD_CONTENTS + "_" + indexTag + "_" + operator.toString() + "_" + p;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public void createDirectories(Path path) throws IOException {
        if (!Files.exists(path))
            Files.createDirectories(path);
    }

    public void searchWithThreads(int numThreads, Collection<ModelBase> models, String runsPath) throws InterruptedException, IOException {

        System.out.println("There are " + models.size() * dataSet.tracks().length + " many tasks to process...");

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);

        for (final Track track : dataSet.tracks()) {

            final Path path = Paths.get(dataSet.collectionPath().toString(), runsPath, indexTag, track.toString());
            createDirectories(path);

            for (final ModelBase model : models) {
                //    executor.execute(new SearcherThread(track, model, QueryParser.Operator.AND, path));
                executor.execute(new SearcherThread(track, model, QueryParser.Operator.OR, path));
            }
        }

        //add some delay to let some threads spawn by scheduler
        Thread.sleep(30000);
        executor.shutdown(); // Disable new tasks from being submitted

        try {
            // Wait for existing tasks to terminate
            while (!executor.awaitTermination(3, TimeUnit.MINUTES)) {
                Thread.sleep(1000);
                //System.out.println(String.format("%.2f percentage completed", (double) executor.getCompletedTaskCount() / executor.getTaskCount() * 100.0d));
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }

        if (executor.getTaskCount() != executor.getCompletedTaskCount())
            throw new RuntimeException("total task count = " + executor.getTaskCount() + " is not equal to completed task count =  " + executor.getCompletedTaskCount());
    }

    public void search(Track track, Similarity similarity, QueryParser.Operator operator, Path path) throws IOException, ParseException {

        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);


        // for (int part = 0; part <= topics.getMaxParts(); part++)
        int part = 0;

        final String runTag = toString(similarity, operator, part);

        PrintWriter out = new PrintWriter(Files.newBufferedWriter(path.resolve(runTag + ".txt"), StandardCharsets.US_ASCII));


        QueryParser queryParser = new QueryParser(FIELD_CONTENTS, Analyzers.analyzer(analyzerTag));
        queryParser.setDefaultOperator(operator);


        for (InfoNeed need : track.getTopics()) {

            String queryString = need.getPartOfQuery(part);
            if (queryString == null) continue;
            Query query = queryParser.parse(queryString);


            ScoreDoc[] hits = searcher.search(query, numHits).scoreDocs;

            /**
             * If you are returning zero documents for a query, instead return the single document
             * clueweb09-en0000-00-00000
             * clueweb12-000000-00-00000
             * GX000-00-0000000
             * If you are returning zero documents for a query, instead return the single document "clueweb09-en0000-00-00000".
             * If you would normally return no documents for a query, instead return the single document "clueweb09-en0000-00-00000" at rank one.
             * Doing so maintains consistent evaluation results (averages over the same number of queries) and does not break anyone's tools.
             */
            if (hits.length == 0) {

                out.print(need.id());
                out.print("\tQ0\t");
                out.print(dataSet.getNoDocumentsID());
                out.print("\t1\t0\t");
                out.print(runTag);
                out.println();
                continue;
            }

            /**
             * the first column is the topic number.
             * the second column is currently unused and should always be "Q0".
             * the third column is the official document identifier of the retrieved document.
             * the fourth column is the rank the document is retrieved.
             * the fifth column shows the score (integer or floating point) that generated the ranking.
             * the sixth column is called the "run tag" and should be a unique identifier for your
             */
            for (int i = 0; i < hits.length; i++) {
                int docId = hits[i].doc;
                Document doc = searcher.doc(docId);
                out.print(need.id());
                out.print("\tQ0\t");
                out.print(doc.get(FIELD_ID));
                out.print("\t");
                out.print(i + 1);
                out.print("\t");
                out.print(hits[i].score);
                out.print("\t");
                out.print(runTag);
                out.println();
                out.flush();
            }
        }

        out.close();
    }

    public void sota(Track track, Similarity similarity) throws IOException, ParseException {


        Map<String, Float> boostMap = new HashMap<>();

        boostMap.put("title", 0.9f);
        boostMap.put("keywords", 0.7f);
        boostMap.put("description", 0.5f);
        boostMap.put("contents", 0.3f);

        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        final Path path = Paths.get(dataSet.collectionPath().toString(), "sota_runs", indexTag, track.toString());
        createDirectories(path);

        final String runTag = "SOTA" + "_" + similarity.toString();
        PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                path.resolve(runTag + ".txt"),
                StandardCharsets.US_ASCII));

        for (InfoNeed need : track.getTopics()) {


            BooleanQuery.Builder bq = new BooleanQuery.Builder().setDisableCoord(true);

            for (String word : Analyzers.getAnalyzedTokens(need.query())) {

                List<Query> queryList = new ArrayList<>();

                for (Map.Entry<String, Float> entry : boostMap.entrySet()) {
                    String field = entry.getKey();
                    float boost = entry.getValue();
                    Term term = new Term(field, word);
                    Query query = new TermQuery(term);
                    query = new BoostQuery(query, boost);

                    queryList.add(query);
                }
                DisjunctionMaxQuery disjunctionMaxQuery = new DisjunctionMaxQuery(queryList, 0.1f);

                bq.add(disjunctionMaxQuery, BooleanClause.Occur.SHOULD);
            }

            int len = need.wordCount();

            if (len < 3)
                // one or two term queries
                bq.setMinimumNumberShouldMatch(len);
            else if (len < 5)
                // three or four terms
                bq.setMinimumNumberShouldMatch(len - 1);
            else
                // five or eight
                bq.setMinimumNumberShouldMatch(len - 2);

            ScoreDoc[] hits = searcher.search(bq.build(), 1000).scoreDocs;

            /**
             * If you are returning zero documents for a query, instead return the single document "clueweb09-en0000-00-00000".
             * If you would normally return no documents for a query, instead return the single document "clueweb09-en0000-00-00000" at rank one.
             * Doing so maintains consistent evaluation results (averages over the same number of queries) and does not break anyone's tools.
             */
            if (hits.length == 0) {

                out.print(need.id());
                out.print("\tQ0\tclueweb09-en0000-00-00000\t1\t0\t");
                out.print(runTag);
                out.println();
                continue;
            }

            for (int i = 0; i < hits.length; i++) {
                int docId = hits[i].doc;
                Document doc = searcher.doc(docId);
                out.print(need.id());
                out.print("\tQ0\t");
                out.print(doc.get(FIELD_ID));
                out.print("\t");
                out.print(i + 1);
                out.print("\t");
                out.print(hits[i].score);
                out.print("\t");
                out.print(runTag);
                out.println();
            }
        }

        out.flush();
        out.close();
    }


    public void verbose(Track track, Similarity similarity, QueryParser.Operator operator) throws IOException, ParseException {

        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);


        final Path verbose_path = Paths.get(dataSet.collectionPath().toString(), "verbose_runs", indexTag, track.toString());
        createDirectories(verbose_path);


        // for (int part = 0; part <= topics.getMaxParts(); part++)
        int part = 0;

        final String runTag = toString(similarity, operator, part);


        PrintWriter verbose_out = new PrintWriter(Files.newBufferedWriter(
                verbose_path.resolve(runTag + ".txt"),
                StandardCharsets.US_ASCII));

        QueryParser queryParser = new QueryParser(FIELD_CONTENTS, Analyzers.analyzer(analyzerTag));
        queryParser.setDefaultOperator(operator);


        for (InfoNeed need : track.getTopics()) {

            String queryString = need.getPartOfQuery(part);
            if (queryString == null) continue;
            Query query = queryParser.parse(queryString);


            /**
             * For Web Tracks 2010,2011,and 2012; an experimental run consists of the top 10,000 documents for each topic query.
             */
            ScoreDoc[] hits = searcher.search(query, 1000).scoreDocs;


            /**
             * the first column is the topic number.
             * the second column is currently unused and should always be "Q0".
             * the third column is the official document identifier of the retrieved document.
             * the fourth column is the rank the document is retrieved.
             * the fifth column shows the score (integer or floating point) that generated the ranking.
             * the sixth column is called the "run tag" and should be a unique identifier for your
             */
            for (int i = 0; i < hits.length; i++) {

                int docId = hits[i].doc;
                Document doc = searcher.doc(docId);

                verbose_out.print(need.id());
                verbose_out.print("\t");
                verbose_out.print(doc.get("id"));
                verbose_out.print("\t");
                verbose_out.print(doc.get(FIELD_CONTENTS + "Length"));
                verbose_out.print("\t");
                verbose_out.print(i + 1);
                verbose_out.print("\t");
                verbose_out.print(hits[i].score);
                verbose_out.print("\t");
                verbose_out.print(similarity.toString());
                verbose_out.print("\t");
                verbose_out.print(need.getJudge(doc.get(FIELD_ID)));
                verbose_out.print("\t");

                if ("DefaultSimilarity".equals(similarity.toString())) continue;
                Explanation explanation = searcher.explain(query, docId);
                Explanation[] first = explanation.getDetails();

                if (first.length != need.wordCount() && QueryParser.Operator.AND.equals(operator))
                    throw new RuntimeException("explanation array size mismatch :" + need.wordCount() + " exp:" + first.length);

                if (need.wordCount() == 1) {

                    try {
                        String word = extractTerm(explanation);
                        String freq = extractFreq(explanation.getDetails()[0]);
                        verbose_out.print(word + "(" + freq + ")=" + explanation.getValue());
                    } catch (RuntimeException r) {
                        System.out.println("analyzer: " + indexTag + " similarity: " + similarity + " " + need.toString());
                        System.out.println(explanation.toString());
                        r.printStackTrace();
                    }

                } else {

                    for (Explanation exp : first)
                        try {

                            String word = extractTerm(exp);
                            String freq = extractFreq(exp.getDetails()[0]);

                            verbose_out.print(word + "(" + freq + ")=" + exp.getValue());
                            verbose_out.print("\t");

                        } catch (RuntimeException r) {
                            System.out.println("analyzer: " + indexTag + "similarity: " + similarity + " " + need.toString());
                            System.out.println(explanation.toString());
                            r.printStackTrace();
                        }
                }

                verbose_out.println();
            }
        }

        verbose_out.flush();
        verbose_out.close();
    }

    public static String extractTerm(Explanation exp) {
        String desc = exp.getDescription();

        int i = desc.indexOf(WEIGHT_CONTENTS);
        if (i == -1) throw new RuntimeException("cannot find term in : " + desc);

        int j = desc.indexOf(" in ", i);
        if (j == -1) throw new RuntimeException("cannot find term in : " + desc);

        return desc.substring(i + WEIGHT_CONTENTS.length(), j);
    }

    public static String extractFreq(Explanation exp) {
        String desc = exp.getDescription();

        int i = desc.indexOf(FREQ);
        if (i == -1) throw new RuntimeException("cannot find freq in : " + desc);

        int j = desc.indexOf("),", i);
        if (j == -1) throw new RuntimeException("cannot find ), in : " + desc);

        return desc.substring(i + FREQ.length(), j);
    }

    public static long extractDocLen(Explanation exp) {
        String desc = exp.getDescription();

        int i = desc.indexOf(DOC_LEN);
        if (i == -1) throw new RuntimeException("cannot find docLen in : " + desc);

        int j = desc.indexOf(",", i);
        if (j == -1) throw new RuntimeException("cannot find , in : " + desc);

        return Long.parseLong(desc.substring(i + DOC_LEN.length(), j));
    }

    public void search(Track track, MATF matf, QueryParser.Operator operator) throws IOException, ParseException {

        IndexSearcher searcher = new IndexSearcher(reader);


        final Path path = Paths.get(dataSet.collectionPath().toString(), "runs", indexTag, track.toString());
        createDirectories(path);


        final String runTag = toString(matf, operator, 0);

        PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                path.resolve(runTag + ".txt"),
                StandardCharsets.US_ASCII));


        QueryParser queryParser = new QueryParser(FIELD_CONTENTS, Analyzers.analyzer(analyzerTag));
        queryParser.setDefaultOperator(operator);


        for (InfoNeed need : track.getTopics()) {

            Query query = queryParser.parse(need.query());


            matf.setMaxOverlap(need.wordCount());
            searcher.setSimilarity(matf);


            /**
             * For Web Tracks 2010,2011,and 2012; an experimental run consists of the top 10,000 documents for each topic query.
             */
            ScoreDoc[] hits = searcher.search(query, 1000).scoreDocs;

            /**
             * If you are returning zero documents for a query, instead return the single document "clueweb09-en0000-00-00000".
             */
            if (hits.length == 0) {

                out.print(need.id());
                out.print("\tQ0\tclueweb09-en0000-00-00000\t1\t0\t");
                out.print(runTag);
                out.println();
                continue;
            }


            /**
             * the first column is the topic number.
             * the second column is currently unused and should always be "Q0".
             * the third column is the official document identifier of the retrieved document.
             * the fourth column is the rank the document is retrieved.
             * the fifth column shows the score (integer or floating point) that generated the ranking.
             * the sixth column is called the "run tag" and should be a unique identifier for your
             */
            for (int i = 0; i < hits.length; i++) {
                int docId = hits[i].doc;
                Document doc = searcher.doc(docId);
                out.print(need.id());
                out.print("\tQ0\t");
                out.print(doc.get(FIELD_ID));
                out.print("\t");
                out.print(i + 1);
                out.print("\t");
                out.print(hits[i].score);
                out.print("\t");
                out.print(runTag);
                out.println();
                out.flush();
            }
        }

        out.close();

    }
}