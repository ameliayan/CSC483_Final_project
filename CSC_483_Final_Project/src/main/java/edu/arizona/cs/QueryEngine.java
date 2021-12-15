package edu.arizona.cs;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;



import edu.stanford.nlp.simple.*;

public class QueryEngine {

    // parseFile
    List<String> tittleList;
    String[] contentList;

    String inputDirPath;
    String indexPath;

    boolean isLemma;
    boolean isStem;

    Analyzer analyzer;
    IndexWriterConfig config;
    Directory index;
    IndexWriter w;
    private String match;

    public QueryEngine(boolean isLemma, boolean isStem) {
        init(isLemma, isStem);

        // check index directory exists
        if (!Files.exists(Paths.get(indexPath))) {
            System.out.println("----- index not exists, building index.....");
            buildIndex();
        } else {
            System.out.println("----- " + Paths.get(indexPath).getFileName() + " exists ");

        }
    }
    public static void main(String[] args) {
        try {
            System.out.println("########### This if CSC483 Final Poroject ########### ");

            boolean isLemma = false;
            boolean isStem = false;

            QueryEngine objQueryEngine = new QueryEngine(isLemma, isStem);
            objQueryEngine.evaluate("src/main/resources/questions.txt");
            
            // //For Q5 improving.
            // objQueryEngine.testAnalyzer();

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }
    private void init(boolean isLemma, boolean isStem) {

        this.isLemma = isLemma;
        this.isStem = isStem;

        if (isStem) {
            analyzer = new EnglishAnalyzer();
            indexPath = "src/main/resources/index_lemma_en/";
            if (isLemma) {
                inputDirPath = "src/main/resources/lemma_wiki2/";
            } 
            else {
                inputDirPath = "src/main/resources/wiki/";
            }
        } 

        else {
            analyzer = new StandardAnalyzer();
            indexPath = "src/main/resources/index_std/";
            if (isLemma) {
                inputDirPath = "src/main/resources/lemma_wiki2/";
            } 
            else {
                inputDirPath = "src/main/resources/wiki/";
            }
        }
    }

    private void buildIndex() {
        try {

            setupIndexWriter();

            File dir = new File(inputDirPath);
            File dirList[] = dir.listFiles();

            for (File file : dirList) {
                System.out.println("----- adding file: " + file.getName() + ".....");
                parseFile(file.getPath());
                addDocToIndex();
            }
            closeIndexWriter();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // parse the given file into 2 lists: tittleList and contentList.
    private void parseFile(String filePath) {
        try {
            String tittle_regex = "\\[\\[.*\\]\\]";

            String fileString = Files.readString(Paths.get(filePath), StandardCharsets.ISO_8859_1);
            fileString = fileString.replaceAll("\\[\\[File:.*\\]\\]", " ");
            fileString = fileString.replaceAll("\\[\\[Image:.*\\]\\]", " ");

            // parse tittles
            tittleList = new ArrayList<>();
            Matcher matcher = Pattern.compile(tittle_regex).matcher(fileString);
            
            while (matcher.find()) {
                match = matcher.group();
                match = match.replaceAll("\\[\\[", "");
                match = match.replaceAll("\\]\\]", "");
                if (isLemma==true) {
                    match = match.replaceAll("\\[ \\[ ", "");
                    match = match.replaceAll(" \\] \\]", "");
                }
                tittleList.add(match);
            }

            contentList = fileString.split(tittle_regex);

            // // parse categories from contents
            // categList = new String[tittleList.size()];
            // Pattern pattern_cat = Pattern.compile("CATEGORIES:(.*)");
            // for (int i = 0; i < categList.length; i++) {
            // Matcher matcher_cat = pattern_cat.matcher(contentList[i + 1]);
            // if (matcher_cat.find()) {
            // // System.out.println(matcher_cat.group());
            // String catline = matcher_cat.group();
            // categList[i] = catline.replaceAll("CATEGORIES:", "");
            // contentList[i + 1] = contentList[i + 1].replaceAll("CATEGORIES:(.*)", "");
            // // System.out.println(contentList[i+1]);
            // } else {
            // categList[i] = "";
            // }
            // }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupIndexWriter() throws IOException {
        config = new IndexWriterConfig(analyzer);
        index = FSDirectory.open(Paths.get(indexPath));
        w = new IndexWriter(index, config);

    }

    private void addDocToIndex() throws IOException {

        for (int i = 0; i <= tittleList.size()-1; i++) {
            String tittle = tittleList.get(i);
            String content = contentList[i + 1];
            // String categories = categList[i];

            Document doc = new Document();
            doc.add(new StringField("tittle", tittle, Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.YES));
            // doc.add(new TextField("categories", categories, Field.Store.YES));
            w.addDocument(doc);
        }
    }

    private void closeIndexWriter() throws IOException {
        w.close();
    }

    // when lemma is true join the string
    private String lemmatize(String s) {
        String res = "";

        edu.stanford.nlp.simple.Document doc = new edu.stanford.nlp.simple.Document(s);
        for (Sentence sent : doc.sentences()) {
            res += String.join(" ", sent.lemmas());
        }
        return res;
    }

    /**
     * search for given query, return the top 1 matched, and print out top 10 matched
     * 
     * @param query a String of questions
     * @return the top 1 matched
     * @throws IOException
     */
    public String search(String query) throws IOException {

        try {
            System.out.println("----- Run query: " + query);

            Query q = new QueryParser("content", analyzer).parse(query);
            System.out.println(q);
            int hitsPerPage = 10;

            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
            IndexSearcher searcher = new IndexSearcher(reader);

            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            System.out.println("----- Found " + hits.length + " hits!");
            for (int i = 0; i <= hits.length-1; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                System.out.println(" " + (i + 1) + ". " + d.get("tittle") + " " + hits[i].score);
            }

            if (hits.length > 0) {
                return searcher.doc(hits[0].doc).get("tittle");
            } else {
                return null;
            }

        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Used for measure the performance
     * 
     * @param query query String
     * @param ans   the correct answer for given query
     * @return the rank of the correct answer
     * @throws IOException
     */
    public int search(String query, String ans) throws IOException {

        int rank = -1;
        try {
            System.out.println("----- Run query: " + query);

            Query q = new QueryParser("content", analyzer).parse(query);
            System.out.println(q);
            int hitsPerPage = 100;

            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
            IndexSearcher searcher = new IndexSearcher(reader);

            // //for Q3 tf-idf score used
            //searcher.setSimilarity(new ClassicSimilarity());

            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            for (int i = 0; i < hits.length; ++i) {
                String n = searcher.doc(hits[i].doc).get("tittle");
                if (ans.toLowerCase().contains(n.toLowerCase())) {
                    rank = i + 1;
                    break;
                }
            }
            System.out.println(rank);

        } catch (ParseException e) {
            e.printStackTrace();
        }
        return rank;
    }

    public void evaluate(String queryFile) {
        try {
            String fileString = Files.readString(Paths.get(queryFile), StandardCharsets.ISO_8859_1);

            String[] questionList = fileString.split("\n\n");

            double MRR = 0;
            int correct_count = 0;
            int within_count = 0;

            for (String q : questionList) {
                String[] items = q.split("\n");
                String categ, content;
                if (isLemma) {
                    categ = lemmatize(items[0]);
                    content = lemmatize(items[1]);
                } else {
                    categ = items[0];
                    content = items[1];
                }
                String query = "(" + categ + ")^1.5 " + content;
                query = query.replaceAll(":", " ");
                query = query.replaceAll("!", "");
                query = query.replaceAll("--", "-");
                query = query.replaceAll("\"", "");

                String answer = items[2];

                // using Mean Reciprocal Rank(MRR)
                int rank = search(query, answer);

                if (rank > 0)
                    MRR += 1.0 / rank;

                // Count correct number
                if (rank == 1)
                    correct_count++;

                if (rank > 1)
                    within_count++;

            }
            MRR = MRR / questionList.length;

            // print out the preformance result
            System.out.println("###################################################");
            System.out.println("######  Lemma: " + isLemma + " Stem: " + isStem + "               ######");
            System.out.println("######  MRR: " + MRR+ "              ######");
            System.out.println("######  Correct: " + correct_count + " / 100" + "                    ######");
            System.out.println("######  Within 2~100: " + within_count + " / 100" + "               ######");
            System.out.println("###################################################");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    // For develop only.
    // print out the tokens after the analyzer analyzed it.
    private void testAnalyzer() {

        try {
            String s = "British Standards are the standards produced by BSI Group which is incorporated under a Royal Charter (and which is formally designated as the National Standards Body (NSB) for the UK). The BSI Group produces British Standards under the authority of the Charter, which lays down as one of the BSI's objectives to: Formally, as per the 2002 Memorandum of Understanding between the BSI and the United Kingdom Government, British Standards are defined as:";

            ArrayList<String> remaining = new ArrayList<String>();

            TokenStream tokenStream = analyzer.tokenStream("contents", new StringReader(s));
            CharTermAttribute term = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                System.out.print("[" + term.toString() + "] ");
                remaining.add(term.toString());
            }
            tokenStream.close();
            analyzer.close();

        } catch (

        IOException e) {
            e.printStackTrace();
        }
    }

}
