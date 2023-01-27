package org.apache.lucene.playground;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.analysis.synonym.SynonymGraphFilterFactory;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
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

        // these props seem crucial
        dumb.setSplitOnWhitespace(true);
        dumb.setAutoGeneratePhraseQueries(true);
        assertEquals("field:\"application program interface\" field:\"user interface\"",
                dumb.parse("API UI").toString());
    }
}
