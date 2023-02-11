package org.apache.lucene.contrb.highly;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Test;

import java.io.IOException;

public class TestLikelyReader extends LuceneTestCase {
    @Test
    public void testFoo() throws IOException {
        final BaseDirectoryWrapper directory = newDirectory();
        final IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig());
        final Document document = new Document();
        document.add(new TextField("name", "foo bar", Field.Store.YES));
        writer.addDocument(document);
        final DirectoryReader reader = DirectoryReader.open(writer, true, true);
        writer.close();
        {
            final LikelyReader likelyReader = new LikelyReader(reader);
            final IndexSearcher searcher = new IndexSearcher(likelyReader);
            final TopDocs topDocs = searcher.search(new TermQuery(new Term("name", "bar")), 1);
            System.out.println(topDocs.scoreDocs[0]);
        }
        {
            final IndexSearcher searcher = new IndexSearcher(reader);
            final TopDocs topDocs = searcher.search(new TermQuery(new Term("name", "bar")), 1);
            System.out.println(topDocs.scoreDocs[0]);
        }
        reader.close();
        directory.close();
    }
}
