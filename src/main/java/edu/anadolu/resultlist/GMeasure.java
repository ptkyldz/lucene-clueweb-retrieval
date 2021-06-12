package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extended version of F measure. Non overlapping documents are given higher rank than the size of the ranking.
 * Note: fMeasure and returns 0 for identical but reverse order lists, but gMeasure does not.
 */
public class GMeasure implements RLFeature {

    @Override
    public double calculate(RLFeatureBase base) {

        List<String> list1 = base.reference.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());
        List<String> list2 = base.alternate.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());

        if (list1.isEmpty() && list2.isEmpty()) return 1.0;

        Set<String> common = new HashSet<>(list1);
        Set<String> only1 = new HashSet<>(list1);
        Set<String> only2 = new HashSet<>(list2);

        common.retainAll(list2); // Intersection
        only1.removeAll(list2); // Difference
        only2.removeAll(list1);

        if (common.size() == 0) return 0.0;
        if (common.size() == 1) return 1.0;

        int k = list1.size();
        int z = common.size();
        double maxF = k * (k + 1);

        double f = 2 * (k - z) * (k + 1);
        for (String doc : common) {
            f += Math.abs(list1.indexOf(doc) - list2.indexOf(doc));
        }
        for (String doc : only1) {
            f -= list1.indexOf(doc) + 1;
        }
        for (String doc : only2) {
            f -= list2.indexOf(doc) + 1;
        }

        return 1.0 - (f / maxF);
    }
}
