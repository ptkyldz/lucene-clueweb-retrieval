package edu.anadolu.resultlist;

import edu.anadolu.analysis.Tag;
import edu.anadolu.cmdline.CLI;
import edu.anadolu.cmdline.CmdLineTool;
import edu.anadolu.datasets.CollectionFactory;
import edu.anadolu.datasets.DataSet;
import edu.anadolu.qpp.Aggregate;
import edu.anadolu.spam.SubmissionFile;
import org.clueweb09.InfoNeed;
import org.clueweb09.tracks.Track;
import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.clueweb09.tracks.Track.whiteSpaceSplitter;

public class ResultListFeaturesTool extends CmdLineTool {

    @Option(name = "-collection", required = true, usage = "Collection")
    protected edu.anadolu.datasets.Collection collection;
    @Option(name = "-tag", required = false, usage = "Index Tag")
    protected String tag = Tag.KStem.toString();
    @Option(name = "-metric", required = false, usage = "Effectiveness Measure")
    protected String metric = "nDCG20";
    @Option(name = "-out", required = false, usage = "Output File Path")
    private String out;
    @Option(name = "-features", required = false, usage = "Feature Files Path")
    private String features;

    //public static final String MODEL_SET = "BM25k1.2b0.75_DirichletLMc2500.0_LGDc1.0_PL2c1.0_DPH_DFIC_DFRee_DLH13";
    public static final String MODEL_SET = "BM25k1.2b0.75";
    //public static final String LTR_SET = "NoLTR_AR_CA_LM_LN_RB_RF_RN_SVM";
    public static final String LTR_SET = "NoLTR_LM"; // First one is the base model

    @Override
    public String getShortDescription() {
        return "Result List Feature Extraction Tool";
    }

    public String getShortModel(String model) {

        if (model.contains("BM25")) return "BM25";
        else if (model.contains("Dirichlet")) return "Dirichlet";
        else if (model.contains("LGD")) return "LGD";
        else if (model.contains("PL2")) return "PL2";
        else return model;
    }

    @Override
    public String getHelp() {
        return "Following properties must be defined in config.properties for " + CLI.CMD + " " + getName() + " paths.indexes tfd.home";
    }

    @Override
    public void run(Properties props) throws Exception {

        if (parseArguments(props) == -1) return;

        final String tfd_home = props.getProperty("tfd.home");

        if ((tfd_home == null) || (collection == null) || (features == null)) {
            System.out.println(getHelp());
            return;
        }

        final long start = System.nanoTime();

        DataSet dataset = CollectionFactory.dataset(collection, tfd_home);

        String[] models = MODEL_SET.split("_");
        String[] ltrs = LTR_SET.split("_");

        String[] modelset = new String[models.length];
        String[] ltrset = new String[ltrs.length];
        for (int i = 0; i < models.length; i++) modelset[i] = getShortModel(models[i]);
        for (int i = 0; i < ltrs.length; i++) ltrset[i] = getShortModel(ltrs[i]);

        Map<String, Map<Integer, Map<String, List<Double>>>> featureMap = getFeatureVectors(features, models);
        Map<String, Map<Integer, RLFeatureBase.ResultList>> resultListMap = new HashMap<>();

        for (String model : models) {
            Map<Integer, List<SubmissionFile.Tuple>> entries = new LinkedHashMap<>();

            int counter = 0;
            for (Track track : dataset.tracks()) {

                Path runsPath = Paths.get(dataset.collectionPath().toString(), "runs", tag, track.toString(), model + "_contents_" + tag + "_" + "OR_all.txt");

                System.out.println("runsPath: " + runsPath);

                if (!Files.exists(runsPath) || !Files.isRegularFile(runsPath) || !Files.isReadable(runsPath))
                    throw new IllegalArgumentException(runsPath + " does not exist or is not a directory.");

                final SubmissionFile submissionFile = new SubmissionFile(runsPath);
                counter += submissionFile.size();
                entries.putAll(submissionFile.entryMap());
            }

            if (counter != entries.size()) throw new RuntimeException("map sizes are not equal!");

            Map<Integer, RLFeatureBase.ResultList> qidResultListMap = new HashMap<>();
            for (Integer qid : entries.keySet()) {
                RLFeatureBase.ResultList resultList = new RLFeatureBase.ResultList(entries.get(qid), featureMap.get(model).get(qid));
                qidResultListMap.put(qid, resultList);
            }

            resultListMap.put(model, qidResultListMap);
        }

        Evaluator evaluator = new Evaluator(dataset, tag, modelset, ltrset, metric);

        // Calculate singular result list features
        for (String model : models) {

            Map<Integer, Integer> labels = evaluator.getLabelMap(getShortModel(model));

            StringBuilder builder = new StringBuilder();

            for (InfoNeed need : dataset.getTopics()) {
                if (!labels.containsKey(need.id()))
                    continue;

                RLFeatureBase.ResultList resultList = resultListMap.get(model).get(need.id());
                double[] scores = new FeatureVector().calculate(resultList, new Aggregate.Average());

                for (int i = 0; i < scores.length; i++) {
                    builder.append(String.format("%.5f", scores[i])).append("\t");
                }
                builder.append(labels.get(need.id())).append("\n");
            }

            //System.out.println(builder);

            try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(out, "RLF_" + collection.toString() + "_" + getShortModel(model) + ".txt"),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                bw.write(builder.toString());
                bw.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Calculate inter-result list features
        List<RLFeature> rlFeatures = new ArrayList<>();
        rlFeatures.add(new OverlappingDocs());
        /*
        for (InfoNeed need : dataset.getTopics()) {
            RLFeatureBase.ResultList reference = resultListMap.get(models[0]).get(need.id());

            List<RLFeature> features = new ArrayList<>();
            features.add(new OverlappingDocs());

            for (int i = 1; i < models.length; i++) {
                RLFeatureBase.ResultList alternate = resultListMap.get(models[i]).get(need.id());
                RLFeatureBase base = new RLFeatureBase(reference, alternate, need);
                System.out.println(base.calculate(features));
            }
        }
         */
        System.out.println("\nResult list features extracted in " + execution(start));
    }

    public Map<String, Map<Integer, Map<String, List<Double>>>> getFeatureVectors(String path, String[] models) {

        Map<String, Map<Integer, Map<String, List<Double>>>> qdFeatureFileMap = new HashMap<>();
        int featureVectorCount = 0;

        try (Stream<Path> walk = Files.walk(Paths.get(path))) {

            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().contains(collection.toString()))
                    .collect(Collectors.toList());

            for (Path file : files) {
                String filename = file.getFileName().toString();
                String model = Arrays.stream(models)
                        .filter(m -> m.startsWith(filename.split("\\.")[1].trim()))
                        .findFirst().orElse(filename.split("\\.")[1].trim());

                Map<Integer, Map<String, List<Double>>> qdFeatureMap = new HashMap<>();

                List<String> lines = Files.readAllLines(file);
                for (String line : lines) {

                    if (line.startsWith("#")) continue;

                    String docID = line.split("#")[1].trim();
                    line = line.split("#")[0].trim();

                    String[] parts = whiteSpaceSplitter.split(line);
                    int qID = Integer.parseInt(parts[1].replace("qid:", ""));
                    List<Double> featureVector = new ArrayList<>();
                    for (int i = 2; i < parts.length; i++)
                        featureVector.add(Double.parseDouble(parts[i].split(":")[1].trim()));

                    if (qdFeatureMap.containsKey(qID)) {
                        qdFeatureMap.get(qID).put(docID, featureVector);
                    } else {
                        Map<String, List<Double>> docFeatureMap = new HashMap<>();
                        docFeatureMap.put(docID, featureVector);
                        qdFeatureMap.put(qID, docFeatureMap);
                    }
                    featureVectorCount++;
                }
                qdFeatureFileMap.put(model, qdFeatureMap);
            }
            System.out.println("\n" + featureVectorCount + " feature vectors for " + collection.toString() + " are loaded.");

        } catch (IOException e) {
            e.printStackTrace();
        }

        return qdFeatureFileMap;
    }
}
