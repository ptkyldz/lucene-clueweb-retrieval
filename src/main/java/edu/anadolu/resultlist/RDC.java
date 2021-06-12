package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rank displacement coefficient.
 * Average change in rank of relevant and non-relevant documents.
 */
public class RDC implements RLFeature {

    @Override
    public double calculate(RLFeatureBase base) {

        List<String> list1 = base.reference.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());
        List<String> list2 = base.alternate.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());

        if (list1.isEmpty() && list2.isEmpty()) return 0;

        Set<String> common1 = new HashSet<>(list1);
        Set<String> common2 = new HashSet<>(list2);

        common1.retainAll(list2); // Intersection
        common2.retainAll(list1);

        if (common1.size() != common2.size()) throw new RuntimeException("list sizes are not equal!");

        int rdc = 0;
        for (String doc : common1) {
            if (isDocRelevant(doc))
                rdc += list1.indexOf(doc) - list2.indexOf(doc);
            else rdc += list2.indexOf(doc) - list1.indexOf(doc);
        }

        return rdc;
    }

    // TODO
    private static boolean isDocRelevant(String doc) {
        return true;
    }
}