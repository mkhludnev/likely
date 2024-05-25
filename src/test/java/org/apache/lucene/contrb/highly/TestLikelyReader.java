package org.apache.lucene.contrb.highly;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestLikelyReader extends LuceneTestCase {
    @Test
    public void testFoo() throws IOException, ParseException {
        // docfreq > 128 (blocksize)
        final BaseDirectoryWrapper directory = newDirectory();
        final IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig());
        int cnt = 1;
        for(String darkColor : Arrays.asList("black", "green","blue")){
            index(writer, darkColor, "tractor", cnt);
            index(writer, darkColor, "truck", cnt);
            cnt++;
        }
        cnt = 1;
        for(String lightColor : Arrays.asList("white", "pink","azure")){
            index(writer, lightColor, "yacht", cnt);
            index(writer, lightColor, "car", cnt);
            cnt ++;
        }
        index(writer, "black", "car", 1);
        index(writer, "white", "tractor", 1);
        for (int i=0; i<2000; i++) { // lets blow up docCount for black car to hurt its score
            index(writer, "black", "stuff"+i, 1);
            index(writer, "stuff"+i, "car", 1);
        }

        final DirectoryReader reader = DirectoryReader.open(writer, true, true);
        writer.close();
        final MultiFieldQueryParser parser = new MultiFieldQueryParser(new String[]{"thing", "color"}, new StandardAnalyzer());

        final LikelyReader likelyReader = new LikelyReader(reader);
        final IndexSearcher likelySearcher = new IndexSearcher(likelyReader);
        final IndexSearcher searcher = new IndexSearcher(reader);

        final Query bothMatch = parser.parse("black car");
        final Query weakMatch = parser.parse("azure tractor");

        assertTrue("Weak match has better absolute score, since it scores one of rare terms." +
                        "It demonstrates that scores between different queries aren't comparable.",
                searcher.search(bothMatch,1).scoreDocs[0].score < searcher.search(weakMatch,1).scoreDocs[0].score);
        assertTrue("but if we normalize it with the most probable score, strong match has higher score",
                howProbable(bothMatch, likelySearcher, searcher) > howProbable(weakMatch, likelySearcher, searcher));
        reader.close();
        directory.close();
    }

    private static float howProbable(Query query, IndexSearcher likelySearcher,
                                     IndexSearcher searcher) throws ParseException, IOException {
        final TopDocs topDocs = likelySearcher.search(query, 1);
        float maxScoreEst = topDocs.scoreDocs[0].score;


        final TopDocs topDocsAct = searcher.search(query, 1);
        final float maxScoreAct = topDocsAct.scoreDocs[0].score;
        System.out.println(""+query + topDocsAct.scoreDocs[0] + " of " + topDocs.scoreDocs[0] +
                " impact's max score:"+maxScore(query,searcher));
        return maxScoreAct / maxScoreEst;
    }

    private static float maxScore(Query query,
                                     IndexSearcher searcher) throws ParseException, IOException {
        List<Term> tqs = new ArrayList<>();
        query.visit(new QueryVisitor() {
            @Override
            public void consumeTerms(Query query, Term... terms) {
                tqs.addAll(Arrays.asList(terms));
            }
        });
        float maxScore =-1;
        for (Term t : tqs) {
            Weight weight = searcher.createWeight(new TermQuery(t), ScoreMode.TOP_SCORES, 1);
            List<LeafReaderContext> leaves = searcher.getIndexReader().leaves();

            for (LeafReaderContext ctx : leaves) {
                Scorer scorer = weight.scorer(ctx);
                if (scorer != null) {
                    scorer.advanceShallow(0);
                    float leafScore = scorer.getMaxScore(ctx.reader().maxDoc() - 1);
                    maxScore = Math.max(maxScore, leafScore);
                }
            }
        }
        return maxScore;
    }

    private static void index(IndexWriter writer, String color, String thing, int tf) throws IOException {
        final Document document = new Document();
        assert tf>0;
        for (int i=0; i<tf; i++) { // mocking tf variety
            document.add(new TextField("thing", color, Field.Store.YES));
            document.add(new TextField("color", thing, Field.Store.YES));
        }
        writer.addDocument(document);
    }
}
