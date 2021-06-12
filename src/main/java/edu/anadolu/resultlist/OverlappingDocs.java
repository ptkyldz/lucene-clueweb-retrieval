package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ratio of the number of common documents in the two rankings and the number of top k documents considered.
 */
public class OverlappingDocs implements RLFeature {

    @Override
    public double calculate(RLFeatureBase base) {

        Set<String> set1 = base.reference.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toSet());
        Set<String> set2 = base.alternate.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toSet());

        if (set1.isEmpty() && set2.isEmpty()) return 1.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2); // Intersection

        return (double) intersection.size() / set1.size();
    }
}
