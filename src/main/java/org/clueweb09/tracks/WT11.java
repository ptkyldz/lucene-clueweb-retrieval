package org.clueweb09.tracks;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * 2011 Web Track topics (query field only)
 * http://trec.nist.gov/data/web/11/queries.101-150.txt
 */
public class WT11 extends Track {

    public WT11(String home) {
        super(home);
    }

    @Override
    protected void populateInfoNeeds() throws IOException {
        populateInfoNeedsWT(Paths.get(home, "topics-and-qrels", "topics.web.101-150.txt"));
    }

    @Override
    protected void populateQRelsMap() throws Exception {
        populateQRelsMap(Paths.get(home, "topics-and-qrels", "qrels.web.101-150.txt"));
    }


    /**
     * top 10,000 documents for 2011 Web Track http://plg.uwaterloo.ca/~trecweb/2011.html
     *
     * @return 10, 000
     */
    @Override
    protected int getTopN() {
        return 10000;
    }

    private static final String[] wt11 = {
            "101:ritz carlton lake las vegas",
            "102:fickle creek farm",
            "103:madam cj walker",
            "104:indiana child support",
            "105:sonoma county medical services",
            "106:universal animal cuts reviews",
            "107:cass county missouri",
            "108:ralph owen brewster",
            "109:mayo clinic jacksonville fl",
            "110:map of brazil",
            "111:lymphoma in dogs",
            //"112:kenmore gas water heater",
            "113:hp mini 2140",
            "114:adobe indian houses",
            "115:pacific northwest laboratory",
            "116:california franchise tax board",
            "117:dangers of asbestos",
            "118:poem in your pocket day",
            "119:interview thank you",
            "120:tv on computer",
            "121:sit and reach test",
            "122:culpeper national cemetery",
            "123:von willebrand disease",
            "124:bowflex power pro",
            "125:butter and margarine",
            "126:us capitol map",
            "127:dutchess county tourism",
            "128:atypical squamous cells",
            "129:iowa food stamp program",
            "130:fact on uranus",
            "131:equal opportunity employer",
            "132:mothers day songs",
            "133:all men are created equal",
            "134:electronic skeet shoot",
            "135:source of the nile",
            "136:american military university",
            "137:rock and gem shows",
            "138:jax chemical company",
            "139:rocky mountain news",
            "140:east ridge high school",
            "141:va dmv registration",
            "142:illinois state tax",
            //"143:arkadelphia health club",
            "144:trombone for sale",
            "145:vines for shade",
            "146:sherwood regional library",
            "147:tangible personal property tax",
            "148:martha stewart and imclone",
            "149:uplift at yellowstone national park",
            "150:tn highway patrol"
    };
}
