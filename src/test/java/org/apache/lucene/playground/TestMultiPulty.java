package org.apache.lucene.playground;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.analysis.synonym.SynonymGraphFilterFactory;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

import java.io.IOException;

public class TestMultiPulty extends LuceneTestCase {
    public void testMultyWordSynonyms() throws IOException, ParseException {
        Analyzer ana = CustomAnalyzer.builder(new ClasspathResourceLoader(TestMultiPulty.class))
                .withTokenizer(StandardTokenizerFactory.NAME)
                .addTokenFilter(LowerCaseFilterFactory.NAME)
                .addTokenFilter(SynonymGraphFilterFactory.NAME, "synonyms", "multy-syn.txt",
                        "ignoreCase", "true") // must, since I have caps in synonyms
                .build();
        QueryParser dumb = new QueryParser("field", ana);
        assertEquals("just booleans", "((+field:bot +field:net) (+field:wifi +field:router))",
                dumb.parse("wifi router").toString());
        assertEquals("phrases", "field:\"bot net\" field:\"wifi router\"",
                dumb.parse("\"wifi router\"").toString());

        // that's what Elasticsearch calls on match_phrase, we got disjunction of all synonymic phrases
        final Query phraseQuery = dumb.createPhraseQuery("field", "experienced API UI jedi");
        assertEquals("field:\"experienced application program interface user interface jedi\" " +
                            "field:\"experienced application program interface ui jedi\" " +
                            "field:\"experienced api user interface jedi\" " +
                            "field:\"experienced api ui jedi\"", phraseQuery.toString());
        // coming to standard entrypoint
        // these props seem crucial
        dumb.setSplitOnWhitespace(true);
        dumb.setAutoGeneratePhraseQueries(true);
        assertEquals("(field:\"application program interface\" field:api) (field:\"user interface\" field:ui)",
                dumb.parse("API UI").toString());

        final Query experienced_api_ui_jedi = dumb.parse("experienced API UI jedi");
        assertEquals("field:experienced (field:\"application program interface\" field:api) (field:\"user interface\" field:ui) field:jedi",
                experienced_api_ui_jedi.toString());
    }
}
