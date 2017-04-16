package edu.anadolu.qpp;

import org.apache.lucene.index.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * contribution of a term <em>t</em> to the total inertia
 */
public class CTI extends Base {

    private final NumericDocValues norms;

    public CTI(Path indexPath) throws IOException {
        super(indexPath, "contents");
        norms = MultiDocValues.getNormValues(reader, field);
    }

    private long getDocLengthFromNorms(int docID) throws IOException {
        return norms.get(docID);
    }

    @Override
    public double value(String word) throws IOException {

        Term term = new Term(field, word);
        PostingsEnum postingsEnum = MultiFields.getTermDocsEnum(reader, field, term.bytes());

        if (postingsEnum == null) return -1;

        double cti = 0.0;

        final double termFrequency = ctf(field, word);

        while (postingsEnum.nextDoc() != PostingsEnum.NO_MORE_DOCS) {

            final double tf = postingsEnum.freq();

            final long docLength = getDocLengthFromNorms(postingsEnum.docID());

            final double e_ij = (termFrequency * docLength) / (double) sumTotalTermFreq;

            cti += Math.pow((tf - e_ij), 2) / e_ij;


        }

        long remainingDocs = docCount - df(field, word);
        double e = termFrequency / docCount;

        cti += remainingDocs * e;
        return cti / docCount;


    }

    public static void main(String[] args) throws IOException {
        try (Base cti = new CTI(Paths.get("/Volumes/clueweb09/indexes/KStemAnalyzer"))) {
            display(cti, new Aggregate.Variance());
        }
    }
}
