package org.apache.lucene.contrb.highly;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

public class TestLikelyReader extends LuceneTestCase {
    @Test
    public void testFoo() throws IOException, ParseException {
        final BaseDirectoryWrapper directory = newDirectory();
        final IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig());
        for(String darkColor : Arrays.asList("black", "green","blue")){
            index(writer, darkColor, "tractor");
            index(writer, darkColor, "truck");
        }
        for(String lightColor : Arrays.asList("white", "pink","azure")){
            index(writer, lightColor, "yacht");
            index(writer, lightColor, "car");
        }
        index(writer, "black", "car");
        index(writer, "white", "tractor");

        final DirectoryReader reader = DirectoryReader.open(writer, true, true);
        writer.close();
        final MultiFieldQueryParser parser = new MultiFieldQueryParser(new String[]{"thing", "color"}, new StandardAnalyzer());
        {
            final LikelyReader likelyReader = new LikelyReader(reader);
            final IndexSearcher likelySearcher = new IndexSearcher(likelyReader);
            final IndexSearcher searcher = new IndexSearcher(reader);
            {
                final float existing = isExpected(parser.parse("black car"), likelySearcher, searcher);

                final float absent = isExpected(parser.parse("azure tractor"), likelySearcher, searcher);
                System.out.println(existing + " > " + absent);
                assertTrue(existing > absent);
            }
        }
        reader.close();
        directory.close();
    }

    private static float isExpected(Query black_car, IndexSearcher likelySearcher,
                                    IndexSearcher searcher) throws ParseException, IOException {
        final TopDocs topDocs = likelySearcher.search(black_car, 1);
        float maxScoreEst = topDocs.scoreDocs[0].score;
        System.out.println(topDocs.scoreDocs[0]);

        final TopDocs topDocsAct = searcher.search(black_car, 1);
        final float maxScoreAct = topDocsAct.scoreDocs[0].score;
        return maxScoreAct / maxScoreEst;
    }

    private static void index(IndexWriter writer, String color, String thing) throws IOException {
        final Document document = new Document();
        document.add(new TextField("thing", color, Field.Store.YES));
        document.add(new TextField("color", thing, Field.Store.YES));
        writer.addDocument(document);
    }
}
