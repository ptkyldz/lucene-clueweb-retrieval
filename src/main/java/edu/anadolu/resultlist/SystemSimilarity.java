package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ratio of Intersection over Union. The Jaccard coefficient measures similarity between finite sample sets,
 * and is defined as the size of the intersection divided by the size of the union of the sample sets.
 *
 * @return Note that by design,  0 <= J(A,B) <=1. If A and B are both empty, define J(A,B) = 1.
 */
public class SystemSimilarity implements RLFeature {

    @Override
    public double calculate(RLFeatureBase base) {

        Set<String> set1 = base.reference.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toSet());
        Set<String> set2 = base.alternate.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toSet());

        if (set1.isEmpty() && set2.isEmpty()) return 1.0;

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2); // Union

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2); // Intersection

        return (double) intersection.size() / union.size();
    }
}
