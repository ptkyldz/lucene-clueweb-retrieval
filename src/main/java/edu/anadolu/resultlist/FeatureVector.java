package edu.anadolu.resultlist;

import edu.anadolu.qpp.Aggregate;

import java.util.Arrays;
import java.util.List;

/**
 * The aggregated feature values of top k documents considered.
 */
public class FeatureVector  {

    public double[] calculate(RLFeatureBase.ResultList resultList, Aggregate aggregate) throws Exception {

        int size = getFeatureSize(resultList);
        if (size == -1) throw new NegativeArraySizeException();

        double[] scores = new double[size];
        Arrays.fill(scores, 0.0);

        for (int i = 0; i < resultList.tuples.size(); i++) {
            String docid = resultList.tuples.get(i).docID;
            List<Double> features = resultList.features.get(docid);

            int score_idx = 0;
            for (double score : features) {
                scores[score_idx++] += score;
            }
        }

        for (int i = 0; i < scores.length; i++) scores[i] /= resultList.tuples.size();

        //return aggregate.aggregate(vector.stream().mapToDouble(Double::doubleValue).toArray());

        return scores;
    }

    public int getFeatureSize(RLFeatureBase.ResultList resultList) {

        int size = 0;
        for (String docid : resultList.features.keySet()) {
            if (size == 0) size = resultList.features.get(docid).size();
            else if (size != resultList.features.get(docid).size()) return -1;
        }

        return size;
    }
}
