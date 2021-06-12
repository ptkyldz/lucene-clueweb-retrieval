package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contribution of documents to the quality of a list. Higher values are assigned to common documents at
 * higher ranks.
 */
public class DocContribution implements RLFeature {

    @Override
    public double calculate(RLFeatureBase base) {

        List<String> list1 = base.reference.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());
        List<String> list2 = base.alternate.tuples.stream().map(SubmissionFile.Tuple::docID).collect(Collectors.toList());

        if (list1.isEmpty() && list2.isEmpty()) return 1.0;

        Set<String> common = new HashSet<>(list1);
        common.retainAll(list2); // Intersection

        //System.out.println("common size: " + common.size());

        if (common.size() == 0) return 0.0;
        if (common.size() == 1) return 1.0;

        double q = 0.0;
        for (String doc : common) {
            q += (1 - ((Math.log(list1.indexOf(doc) + 1)) / (Math.log(list1.size()))));
            //System.out.println("index: " + list1.indexOf(doc) + " value: " + (1 - ((Math.log(list1.indexOf(doc) + 1)) / (Math.log(list1.size())))));
        }

        return q / list1.size();
    }
}
