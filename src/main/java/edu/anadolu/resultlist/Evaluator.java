package edu.anadolu.resultlist;

import edu.anadolu.datasets.DataSet;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import org.clueweb09.InfoNeed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Evaluator {

    final WilcoxonSignedRankTest wilcoxonSignedRankTest = new WilcoxonSignedRankTest();
    final TTest tTest = new TTest();

    private Map<String, Double> scoreMap;

    protected DataSet dataset;
    protected List<InfoNeed> needs;

    protected final String[] modelSet;
    protected final String[] ltrSet;

    protected final String metric;
    protected final String tag;

    public Evaluator(DataSet dataset, String tag, String[] modelSet, String[] ltrSet, String metric) {

        this.dataset = dataset;
        this.tag = tag;
        this.needs = dataset.getTopics();
        this.modelSet = modelSet;
        this.ltrSet = ltrSet;
        this.metric = metric;

        populateScoreMap();
    }

    public void populateScoreMap() {

        Map<String, Double> scoreMap = new TreeMap<>();

        for (String model : modelSet) {
            for (String ltr : ltrSet) {
                for (InfoNeed need : needs) {
                    final String key = need.id() + "_" + model + "_" + ltr;
                    Eval eval = getEval(model, ltr);
                    if (eval.scores.get(need.id()) == null) continue;
                    scoreMap.put(key, eval.scores.get(need.id()));
                }
            }
        }

        this.scoreMap = Collections.unmodifiableMap(scoreMap);
    }

    public Eval getEval(String model, String ltr) {

        Path evalsPath = Paths.get(dataset.collectionPath().toString(), "ltr_evals", tag, model + "." + ltr + "." + metric + ".txt");
        if (!Files.exists(evalsPath) || !Files.isRegularFile(evalsPath) || !Files.isReadable(evalsPath))
            throw new IllegalArgumentException(evalsPath + " does not exist or is not a directory.");

        try {
            List<String> lines = Files.readAllLines(evalsPath, StandardCharsets.US_ASCII);

            Map<Integer, Double> scores = new HashMap<>();
            int qid;
            double score;

            for (String line : lines) {
                if (line.startsWith("runid") || line.contains("amean"))
                    continue;

                String[] parts = line.split(",");
                qid = Integer.parseInt(parts[1]);
                score = Double.parseDouble(parts[2]);
                scores.put(qid, score);
            }

            return new Eval(model, ltr, scores);

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        throw new RuntimeException("Eval file : " + evalsPath + " cannot be found!");
    }

    public void printLabels() {

        for (String model : modelSet) {
            System.out.println("model: " + model);

            Map<Integer, Integer> labelMap = getLabelMap(model);
            for (Integer qid : labelMap.keySet()) {
                System.out.print(" qid: " + qid + " label: " + labelMap.get(qid) + "\n");
            }
        }
    }

    public Map<Integer, Integer> getLabelMap(String model) {

        Map<Integer, Integer> labelMap = new HashMap<>();

        String base = ltrSet[0];
        for (int i = 1; i < ltrSet.length; i++) {
            for (InfoNeed need : needs) {

                try {
                    if (score(need, model, ltrSet[i]) > score(need, model, base))
                        labelMap.put(need.id(), 1);
                    else labelMap.put(need.id(), 0);

                } catch (RuntimeException e) {
                    System.out.println(e.getMessage());
                }
            }
        }

        return labelMap;
    }

    public void displayPValues() {

        String base = ltrSet[0];

        for (String model : modelSet) {
            for (int i = 1; i < ltrSet.length; i++) {

                double tP = tTest.pairedTTest(scoreArray(model, base), scoreArray(model, ltrSet[i])) / 2d;
                double wP = wilcoxonSignedRankTest.wilcoxonSignedRankTest(scoreArray(model, base), scoreArray(model, ltrSet[i]), false);

                if (tP < 0.05 || wP < 0.05)
                    System.out.println(model + "_" + ltrSet[i] + "(tp:" + String.format("%.10f", tP) + "; wp:" + String.format("%.10f", wP) + ") ");
                else
                    System.out.println("*" + model + "_" + ltrSet[i] + "(tp:" + String.format("%.10f", tP) + "; wp:" + String.format("%.10f", wP) + ") ");
            }
        }

        System.out.println();
    }

    public double score(InfoNeed need, String model, String ltr) {

        final String key = need.id() + "_" + model + "_" + ltr;

        if (!scoreMap.containsKey(key)) {
            throw new RuntimeException(need.id() + ":" + need.query() + " does not have eval score. Skipping...");
        } else {
            return scoreMap.get(key);
        }
    }

    public double[] scoreArray(String model, String ltr) {

        double scores[] = new double[needs.size()];
        Arrays.fill(scores, 0.0);

        int c = 0;
        for (InfoNeed need : needs) {

            try {
                scores[c] = score(need, model, ltr);
                c++;
            } catch (RuntimeException e) {
                continue;
            }
        }

        return scores;
    }

    public static class Eval {

        String model;
        String ltr;
        Map<Integer, Double> scores;
        Double average;

        public Eval(String model, String ltr, Map<Integer, Double> scores) {
            this.model = model;
            this.ltr = ltr;
            this.scores = scores;
            this.average = computeAverage();
        }

        public double computeAverage() {

            double avg = 0.0;
            for (int qid : scores.keySet())
                avg += scores.get(qid);

            return avg/scores.size();
        }
    }
}
