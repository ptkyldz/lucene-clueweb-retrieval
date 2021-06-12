package edu.anadolu.resultlist;

import edu.anadolu.spam.SubmissionFile;
import org.clueweb09.InfoNeed;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RLFeatureBase {

    ResultList reference;
    ResultList alternate;
    InfoNeed query;

    RLFeatureBase(ResultList reference, ResultList alternate, InfoNeed query) {

        this.reference = reference;
        this.alternate = alternate;
        this.query = query;
    }

    String calculate(List<RLFeature> featureList) throws IOException {

        StringBuilder builder = new StringBuilder();

        for (RLFeature rlFeature : featureList) {
            double score = rlFeature.calculate(this);
            builder.append(String.format("%.5f", score)).append("\t");
        }

        return builder.toString();
    }

    static class ResultList {

        List<SubmissionFile.Tuple> tuples;
        Map<String, List<Double>> features;

        ResultList(List<SubmissionFile.Tuple> tuples, Map<String, List<Double>> features) {
            this.tuples = tuples;
            this.features = features;
        }

        @Override
        public String toString() {
            String str = "No of tuples: " + tuples.size() + "\n";
            str += "No of documents: " + features.keySet().size() + "\n";
            for (String key : features.keySet()) {
                str += "No of features for " + key + " : " + features.get(key).size() + "\n";
            }
            return str;
        }
    }
}
