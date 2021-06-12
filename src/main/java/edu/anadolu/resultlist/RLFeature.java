package edu.anadolu.resultlist;

import java.io.IOException;

public interface RLFeature {

    double calculate(RLFeatureBase base) throws IOException;
}
