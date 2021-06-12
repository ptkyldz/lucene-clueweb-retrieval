package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The rank differences of overlapping documents between two rankings. Normalized and subtracted from 1 version of
 * Spearman's Footrule. Defined for identical rankings. For non identical rankings, non overlapping documents are
 * removed and remaining documents are given relative rank.
 */
public class FMeasure implements RLFeature {

    @Override
    public double calculate(RLFeatureBase base) {

        List<String> list1 = base.reference.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());
        List<String> list2 = base.alternate.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());

        if (list1.isEmpty() && list2.isEmpty()) return 1.0;

        list1.retainAll(list2); //Intersection
        list2.retainAll(list1);

        if (list1.size() == 0) return 0.0;
        if (list1.size() == 1) return 1.0;

        int S = list1.size();
        double maxFr = (S % 2 == 0) ? (0.5 * Math.pow(S, 2)) : (0.5 * (S - 1) * (S + 1));

        int fr = 0;
        for (int i = 0; i < list1.size(); i++) {
            fr += Math.abs(i - list2.indexOf(list1.get(i)));
        }

        return 1.0 - (fr / maxFr);
    }
}
