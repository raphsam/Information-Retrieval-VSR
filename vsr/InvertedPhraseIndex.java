package ir.vsr;

import java.io.*;
import java.util.*;
import java.lang.*;

import ir.utilities.*;
import ir.classifiers.*;

/**
 * An inverted index for vector-space information retrieval. Contains
 * methods for creating an inverted index from a set of documents
 * and retrieving ranked matches to queries using standard TF/IDF
 * weighting and cosine similarity.
 *
 * @author Ray Mooney
 */
public class InvertedPhraseIndex {

  /**
   * The maximum number of retrieved documents for a query to present to the user
   * at a time
   */
  public static final int MAX_RETRIEVALS = 10;
  
  // Maximum number of bigrams considered
  public static final int MAX_PHRASES = 1000;

  // Map to keep track of all bigrams
  public static Map<String, TokenInfo> bigramHash = null;
  


  /**
   * A list of all indexed documents.  Elements are DocumentReference's.
   */
  public List<DocumentReference> docRefs = null;

  /**
   * The directory from which the indexed documents come.
   */
  public File dirFile = null;

  /**
   * The type of Documents (text, HTML). See docType in DocumentIterator.
   */
  public short docType = DocumentIterator.TYPE_TEXT;

  /**
   * Whether tokens should be stemmed with Porter stemmer
   */
  public boolean stem = false;

  /**
   * Whether relevance feedback using the Ide_regular algorithm is used
   */
  public boolean feedback = false;

  /**
   * Create an inverted index of the documents in a directory.
   *
   * @param dirFile  The directory of files to index.
   * @param docType  The type of documents to index (See docType in DocumentIterator)
   * @param stem     Whether tokens should be stemmed with Porter stemmer.
   * @param feedback Whether relevance feedback should be used.
   */
  public InvertedPhraseIndex(File dirFile, short docType, boolean stem, boolean feedback) {
    this.dirFile = dirFile;
    this.docType = docType;
    this.stem = stem;
    this.feedback = feedback;
    bigramHash = new HashMap<String, TokenInfo>(); 
    docRefs = new ArrayList<DocumentReference>();
    indexPhraseDocuments();
  }

  /**
   * Create an inverted index of the documents in a List of Example objects of documents
   * for text categorization.
   *
   * @param examples A List containing the Example objects for text categorization to index
   */
  public InvertedPhraseIndex(List<Example> examples) {
    bigramHash = new HashMap<String, TokenInfo>(); 
    docRefs = new ArrayList<DocumentReference>();
    indexPhraseDocuments(examples);
  }


  /**
   * Index the documents in dirFile. 
   */
  protected void indexPhraseDocuments() {
    if (!bigramHash.isEmpty() || !docRefs.isEmpty()) { 
      // Currently can only index one set of documents when an index is created
      throw new IllegalStateException("Cannot indexDocuments more than once in the same InvertedPhraseIndex");
    }
    // Get an iterator for the documents
    DocumentIterator docIter = new DocumentIterator(dirFile, docType, stem);
    System.out.println("Reviewing documents in " + dirFile);
    // Loop, processing each of the documents

    while (docIter.hasMoreDocuments()) { 
      FileDocument doc = docIter.nextDocument();
      // Create a document vector for this document
      System.out.print(doc.file.getName() + ",");
      HashMapVector vector = doc.hashMapBigramVector();
      indexPhraseDocument(doc, vector); 
    }
    // Now that all documents have been processed, we can calculate the IDF weights for
    // all tokens and the resulting lengths of all weighted document vectors.
    //computeIDFandDocumentLengths(); removed
    //System.out.println("\nIndexed " + docRefs.size() + " documents with " + size() + " unique terms."); removed
  }


  /**
   * Index the documents in the List of Examples for text categorization.
   */
  public void indexPhraseDocuments(List<Example> examples) {
    if (!bigramHash.isEmpty() || !docRefs.isEmpty()) {
      // Currently can only index one set of documents when an index is created
      throw new IllegalStateException("Cannot indexDocuments more than once in the same InvertedPhraseIndex");
    }
    // Loop, processing each of the examples
    for (Example example : examples) {
      FileDocument doc = example.getDocument();
      // Create a document vector for this document
      HashMapVector vector = example.getHashMapVector();
      indexPhraseDocument(doc, vector);
    }
    // Now that all documents have been processed, we can calculate the IDF weights for
    // all tokens and the resulting lengths of all weighted document vectors.
    //computeIDFandDocumentLengths();
    //System.out.println("Indexed " + docRefs.size() + " documents with " + size() + " unique terms.");
  }

  /**
   * Index the given document using its corresponding vector
   */
  protected void indexPhraseDocument(FileDocument doc, HashMapVector vector) {
    // Create a reference to this document
    DocumentReference docRef = new DocumentReference(doc);
    // Add this document to the list of documents indexed
    docRefs.add(docRef);
    // Iterate through each of the tokens in the document
    for (Map.Entry<String, Weight> entry : vector.entrySet()) {
      // An entry in the HashMap maps a token to a Weight
      String token = entry.getKey();
      // The count for the token is in the value of the Weight
      int count = (int) entry.getValue().getValue();
      // Add an occurrence of this token to the inverted index pointing to this document
      indexPhraseToken(token, count, docRef);
    }
  }

  /**
   * Add a token occurrence to the index.
   *
   * @param token  The token to index.
   * @param count  The number of times it occurs in the document.
   * @param docRef A reference to the Document it occurs in.
   */
  protected void indexPhraseToken(String token, int count, DocumentReference docRef) {
    // Find this token in the index
    TokenInfo tokenInfo = bigramHash.get(token);
    if (tokenInfo == null) {
      // If this is a new token, create info for it to put in the hashtable
      tokenInfo = new TokenInfo();
      bigramHash.put(token, tokenInfo);
    }
    // Add a new occurrence for this token to its info
    tokenInfo.occList.add(new TokenOccurrence(docRef, count));
  }

  /**
   * Print out an inverted index by listing each token and the documents it occurs in.
   * Include info on IDF factors, occurrence counts, and document vector lengths.
   */
     public void print() {
    // Iterate through each token in the index
    for (Map.Entry<String, TokenInfo> entry : bigramHash.entrySet()) {
      String token = entry.getKey();
      // Print the token and its IDF factor
      System.out.println(token + " (IDF=" + entry.getValue().idf + ") occurs in:");
      // For each document referenced, print its name, occurrence count for this token, and
      // document vector length (|D|).
      for (TokenOccurrence occ : entry.getValue().occList) {
        System.out.println("   " + occ.docRef.file.getName() + " " + occ.count +
            " times; |D|=" + occ.docRef.length);
      }
    }
  }

  /**
   * Return the number of tokens indexed.
   */
  public int size() {
    return bigramHash.size();
  }

  /**
   * Clear all documents from the inverted index
   */
  public void clear() {
    docRefs.clear();
    bigramHash.clear();
  }

  /**
   * Perform ranked retrieval on this input query.
   */
  public Retrieval[] retrieve(String input) {
    return retrieve(new TextStringDocument(input, stem));
  }

  /**
   * Perform ranked retrieval on this input query Document.
   */
  public Retrieval[] retrieve(Document doc) {
    return retrieve(doc.hashMapVector());
  }

  /**
   * Perform ranked retrieval on this input query Document vector.
   */
   
  public Retrieval[] retrieve(HashMapVector vector) {
    // Create a hashtable to store the retrieved documents.  Keys
    // are docRefs and values are DoubleValues which indicate the
    // partial score accumulated for this document so far.
    // As each token in the query is processed, each document
    // it indexes is added to this hashtable and its retrieval
    // score (similarity to the query) is appropriately updated.
    Map<DocumentReference, DoubleValue> retrievalHash =
        new HashMap<DocumentReference, DoubleValue>();
    // Initialize a variable to store the length of the query vector
    double queryLength = 0.0;
    // Iterate through each token in the query input Document
    for (Map.Entry<String, Weight> entry : vector.entrySet()) {
      String token = entry.getKey();
      double count = entry.getValue().getValue();
      // Determine the score added to the similarity of each document
      // indexed under this token and update the length of the
      // query vector with the square of the weight for this token.
      queryLength = queryLength + incorporateToken(token, count, retrievalHash);
    }
    // Finalize the length of the query vector by taking the square-root of the
    // final sum of squares of its token weights.
    queryLength = Math.sqrt(queryLength);
    // Make an array to store the final ranked Retrievals.
    Retrieval[] retrievals = new Retrieval[retrievalHash.size()];
    // Iterate through each of the retrieved documents stored in
    // the final retrievalHash.
    int retrievalCount = 0;
    for (Map.Entry<DocumentReference, DoubleValue> entry : retrievalHash.entrySet()) {
      DocumentReference docRef = entry.getKey();
      double score = entry.getValue().value;
      retrievals[retrievalCount++] = getRetrieval(queryLength, docRef, score);
    }
    // Sort the retrievals to produce a final ranked list using the
    // Comparator for retrievals that produces a best to worst ordering.
    Arrays.sort(retrievals);
    return retrievals;
  }

  /**
   * Calculate the final score for a retrieval and return a Retrieval object representing
   * the retrieval with its final score.
   *
   * @param queryLength The length of the query vector, incorporated into the final score
   * @param docRef The document reference for the document concerned
   * @param score The partially computed score 
   * @return The retrieval object for the document described by docRef
   *     and score under the query with length queryLength
   */
  protected Retrieval getRetrieval(double queryLength, DocumentReference docRef, double score) {
    // Normalize score for the lengths of the two document vectors
    score = score / (queryLength * docRef.length);
    // Add a Retrieval for this document to the result array
    return new Retrieval(docRef, score);
  }

  public double incorporateToken(String token, double count,
                                 Map<DocumentReference, DoubleValue> retrievalHash) {
    TokenInfo tokenInfo = bigramHash.get(token);
    // If token is not in the index, it adds nothing and its squared weight is 0
    if (tokenInfo == null) return 0.0;
    // The weight of a token in the query is is IDF factor times the number
    // of times it occurs in the query.
    double weight = tokenInfo.idf * count;
    // For each document occurrence indexed for this token...
    for (TokenOccurrence occ : tokenInfo.occList) {
      // Get the current score for this document in the retrievalHash.
      DoubleValue val = retrievalHash.get(occ.docRef);
      if (val == null) {
        // If this is a new retrieved document, create an initial score
        // for it and store in the retrievalHash
        val = new DoubleValue(0.0);
        retrievalHash.put(occ.docRef, val);
      }
      // Update the score for this document by adding the product
      // of the weight of this token in the query and its weight
      // in the retrieved document (IDF * occurrence count)
      val.value = val.value + weight * tokenInfo.idf * occ.count;
    }
    // Return the square of the weight of this token in the query
    return weight * weight;
  }

  /**
   * Show the top retrievals to the user if there are any.
   *
   * @return true if retrievals are non-empty.
   */
  public boolean showRetrievals(Retrieval[] retrievals) {
    if (retrievals.length == 0) {
      System.out.println("\nNo matching documents found.");
      return false;
    } else {
      System.out.println("\nTop " + MAX_RETRIEVALS + " matching Documents from most to least relevant:");
      printRetrievals(retrievals, 0);
      System.out.println("\nEnter `m' to see more, a number to show the nth document, nothing to exit.");
      if (feedback)
        System.out.println("Enter `r' to use any relevance feedback given to `redo' with a revised query.");
      return true;
    }
  }


  /**
   * Print out at most MAX_RETRIEVALS ranked retrievals starting at given starting rank number.
   * Include the rank number and the score.
   */
  public void printRetrievals(Retrieval[] retrievals, int start) {
    System.out.println("");
    if (start >= retrievals.length)
      System.out.println("No more retrievals.");
    for (int i = start; i < Math.min(retrievals.length, start + MAX_RETRIEVALS); i++) {
      System.out.println(MoreString.padTo((i + 1) + ". ", 4) +
          MoreString.padTo(retrievals[i].docRef.file.getName(), 20) +
          " Score: " +
          MoreMath.roundTo(retrievals[i].score, 5));
    }
  }
  
  public static Map<String, TokenInfo> getBigramHash() {
	  return bigramHash;
  }

  /**
   * Index a directory of files and then interactively accept retrieval queries.
   * Command format: "InvertedIndex [OPTION]* [DIR]" where DIR is the name of
   * the directory whose files should be indexed, and OPTIONs can be
   * "-html" to specify HTML files whose HTML tags should be removed.
   * "-stem" to specify tokens should be stemmed with Porter stemmer.
   * "-feedback" to allow relevance feedback from the user.
   */
  public static void main(String[] args) {
    // Parse the arguments into a directory name and optional flag

    String dirName = args[args.length - 1];
    short docType = DocumentIterator.TYPE_TEXT;
    boolean stem = false, feedback = false;
    for (int i = 0; i < args.length - 1; i++) {
      String flag = args[i];
      if (flag.equals("-html"))
        // Create HTMLFileDocuments to filter HTML tags
        docType = DocumentIterator.TYPE_HTML;
      else if (flag.equals("-stem"))
        // Stem tokens with Porter stemmer
        stem = true;
      else if (flag.equals("-feedback"))
        // Use relevance feedback
        feedback = true;
      else {
        throw new IllegalArgumentException("Unknown flag: "+ flag);
      }
    }


    // Call for the first pass to find all bigrams
    InvertedPhraseIndex phraseIndex = new InvertedPhraseIndex(new File(dirName), docType, stem, feedback);
	
	// Create List of bigrams
	List<Map.Entry<String,TokenInfo>> entries = new ArrayList<Map.Entry<String,TokenInfo>>(bigramHash.entrySet());
	
	// Do a collections sort over the bigrams list based on occurence
	Collections.sort(entries, new Comparator<Map.Entry<String,TokenInfo>>() {
        public int compare(Map.Entry<String,TokenInfo> a, Map.Entry<String,TokenInfo> b) {
			int count1 = 0;
			int count2 = 0;
			
			// Take sum off occurence counts in each token occurence
			for (int i = 0; i < a.getValue().occList.size(); i++) {
				count1 += a.getValue().occList.get(i).count;
			}	
			for (int i = 0; i < b.getValue().occList.size(); i++) {
				count2 += b.getValue().occList.get(i).count;
			}
            return Integer.compare(count2, count1);
        }
    } );
	
	
	int count1;
	int phraseCount = 0;
	Iterator<Map.Entry<String,TokenInfo>> iter = entries.iterator();
	
	
	// Iterate through the list of bigrams. Get sum/print number of occurences
	// For the rest of the bigrams that did not make the top 1000 list, delete it
	while(iter.hasNext()) { 
		Map.Entry<String,TokenInfo> entry = iter.next(); 
		if (phraseCount < 1000) {
			// get sum
			count1 = 0;
			for (int x = 0; x < entry.getValue().occList.size(); x++) {
				count1 += entry.getValue().occList.get(x).count;
			} 
			// print phrase and frequency
			System.out.println(entry.getKey() + ": "+ count1);
			phraseCount++;
		}
		// delete extras
		else
			bigramHash.remove(entry.getKey());
	}
	

	// Calls the second pass that tokenizes everything
	InvertedIndex index = new InvertedIndex(new File(dirName), docType, stem, feedback);
    // index.print(); 
    // Interactively process queries to this index. 
    index.processQueries();
  }
  
 }



   
