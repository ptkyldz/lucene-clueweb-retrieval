package edu.anadolu.cmdline;

import edu.anadolu.Searcher;
import edu.anadolu.datasets.CollectionFactory;
import edu.anadolu.datasets.DataSet;
import edu.anadolu.eval.Evaluator;
import edu.anadolu.knn.Measure;
import edu.anadolu.similarities.DFIC;
import edu.anadolu.similarities.DFRee;
import edu.anadolu.similarities.DLH13;
import edu.anadolu.similarities.DPH;
import org.apache.lucene.search.similarities.ModelBase;
import org.kohsuke.args4j.Option;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static edu.anadolu.cmdline.ParamTool.train;

public class CustomTool extends CmdLineTool {

    @Option(name = "-tag", metaVar = "[KStem|KStemAnchor]", required = true, usage = "Index Tag")
    protected String tag;

    /**
     * Terrier's default values
     */
    @Option(name = "-models", required = false, usage = "term-weighting models")
    protected String models = "BM25k1.2b0.75_DirichletLMc2500.0_LGDc1.0_PL2c1.0";

    @Option(name = "-metric", required = false, usage = "Effectiveness measure")
    protected Measure measure = Measure.NDCG20;

    @Option(name = "-collection", required = true, usage = "Collection")
    protected edu.anadolu.datasets.Collection collection;

    @Option(name = "-task", required = false, usage = "task to be executed: search or eval")
    private String task;

    @Override
    public String getShortDescription() {
        return "Searcher Tool in which you supply values of the hyper parameters";
    }

    @Override
    public String getHelp() {
        return "Following properties must be defined in config.properties for " + CLI.CMD + " " + getName() + " paths.indexes tfd.home";
    }


    @Override
    public void run(Properties props) throws Exception {

        if (parseArguments(props) == -1) return;

        final String tfd_home = props.getProperty("tfd.home");

        if (tfd_home == null) {
            System.out.println(getHelp());
            return;
        }

        DataSet dataset = CollectionFactory.dataset(collection, tfd_home);

        final int numThreads = Integer.parseInt(props.getProperty("numThreads", "2"));

        if ("search".equals(task)) {


            final long start = System.nanoTime();

            final Set<ModelBase> modelBaseSet = Arrays.stream(models.split("_"))
                    .map(ParamTool::string2model)
                    .collect(Collectors.toSet());

            modelBaseSet.add(new DFIC());
            modelBaseSet.add(new DPH());
            modelBaseSet.add(new DLH13());
            modelBaseSet.add(new DFRee());

            if (!props.containsKey(collection.toString() + ".fields"))
                throw new RuntimeException("cannot find " + collection.toString() + ".fields property!");

            final String[] fieldsArr = props.getProperty(collection.toString() + ".fields").split(",");

            List<String> fields = Arrays.asList(fieldsArr);
            for (final Path path : discoverIndexes(dataset)) {

                final String tag = path.getFileName().toString();

                // search for a specific tag, skip the rest
                if (this.tag != null && !tag.equals(this.tag)) continue;

                try (Searcher searcher = new Searcher(path, dataset, 1000)) {
                    searcher.searchWithThreads(numThreads, modelBaseSet, /*Collections.singletonList("contents")*/ fields, "runs");
                }
            }
            System.out.println("Search completed in " + execution(start));
            return;
        }

        if ("eval".equals(task)) {

            Evaluator evaluator = new Evaluator(dataset, tag, measure, models + "_DFIC_DPH_DFRee_DLH13", "evals", "OR");
            evaluator.printMeanWT();
            evaluator.printMean();
            System.out.println("=======================");
        }
        if ("spam".equals(task)) {

            final long start = System.nanoTime();

            final Set<ModelBase> modelBaseSet = Arrays.stream(models.split("_"))
                    .map(ParamTool::string2model)
                    .collect(Collectors.toSet());

            modelBaseSet.add(new DFIC());
            modelBaseSet.add(new DPH());
            modelBaseSet.add(new DLH13());
            modelBaseSet.add(new DFRee());

            if (!props.containsKey(collection.toString() + ".fields"))
                throw new RuntimeException("cannot find " + collection.toString() + ".fields property!");

            final String[] fieldsArr = props.getProperty(collection.toString() + ".fields").split(",");

            List<String> fields = Arrays.asList(fieldsArr);
            if(fields.size()==0) fields.add( props.getProperty(collection.toString() + ".fields"));

            if (dataset.spamAvailable()) {
                for (final Path path : discoverIndexes(dataset)) {

                    final String tag = path.getFileName().toString();

                    // search for a specific tag, skip the rest
                    if (this.tag != null && !tag.equals(this.tag)) continue;

                    try (Searcher searcher = new Searcher(path, dataset, 10000)) {
                        searcher.searchWithThreads(numThreads, modelBaseSet, fields, "base_spam_runs");
                    }
                    modelBaseSet.clear();
                }

                System.out.println("Base search for spam filtering 10,000 documents per query completed in " + execution(start));
                return;
            }
        }

    }
}
