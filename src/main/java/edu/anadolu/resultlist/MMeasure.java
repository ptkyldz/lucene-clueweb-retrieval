package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Note: fMeasure and returns 0 for identical but reverse order lists, but mMeasure does not.
 */
public class MMeasure implements RLFeature {

    @Override
    public double calculate(RLFeatureBase base) {

        List<String> list1 = base.reference.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());
        List<String> list2 = base.alternate.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());

        if (list1.isEmpty() && list2.isEmpty()) return 1.0;

        Set<String> common1 = new HashSet<>(list1);
        Set<String> common2 = new HashSet<>(list2);
        Set<String> only1 = new HashSet<>(list1);
        Set<String> only2 = new HashSet<>(list2);

        common1.retainAll(list2); // Intersection
        common2.retainAll(list1);
        only1.removeAll(list2); // Difference
        only2.removeAll(list1);

        if (common1.size() == 0) return 0.0;
        if (common1.size() == 1) return 1.0;

        int k = list1.size();
        double maxF = 16.1892089799257;  // how to calculate the normalization factor? still negatives values exist

        double m = 0.0;
        for (String doc : common1) {
            m += Math.abs((1.0 / (list1.indexOf(doc) + 1)) - (1.0 / (list2.indexOf(doc) + 1)));
        }
        for (String doc : only1) {
            m += (1.0 / (list1.indexOf(doc) + 1)) - (1.0 / (k + 1));
        }
        for (String doc : only2) {
            m += (1.0 / (list2.indexOf(doc) + 1)) - (1.0 / (k + 1));
        }

        System.out.println("m: " + m);
        return 1.0 - (m / maxF);
    }
}