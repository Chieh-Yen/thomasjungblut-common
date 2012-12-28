package de.jungblut.crawl.extraction;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.SimpleNodeIterator;
import org.mozilla.universalchardet.UniversalDetector;

import de.jungblut.crawl.FetchResult;

/**
 * Outlink extractor, parses a page just for its outlinks.
 * 
 * @author thomas.jungblut
 * 
 */
public final class OutlinkExtractor implements Extractor<FetchResult> {

  private static final int BUFFER_SIZE = 64 * 1024;
  private static final String USER_AGENT_KEY = "User-Agent";
  private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:2.0.1) Gecko/20100101 Firefox/4.0.1";
  private static final NodeFilter LINK_FILTER = new NodeClassFilter(
      LinkTag.class);

  private static final Pattern IGNORE_SUFFIX_PATTERN = Pattern
      .compile(".*(\\.(css|js|bmp|gif|jpe?g|png|tiff?|mid|mp2|mp3|mp4|wav|avi|mov|mpeg|ram|m4v|pdf|rm|smil|wmv|swf|wma|zip|rar|gz))$");

  private static final Pattern BASE_URL = Pattern
      .compile("(http[s]*://[a-z0-9.]+)");

  @Override
  public FetchResult extract(String realUrl) {
    if (realUrl == null || !realUrl.startsWith("http")
        || realUrl.length() > 500)
      return null;
    try {

      InputStream connection = getConnection(realUrl);
      String html = consumeStream(connection);

      final HashSet<String> set = extractOutlinks(html, realUrl);

      return new FetchResult(realUrl, set);
    } catch (ParserException pEx) {
      // ignore parser exceptions, they contain mostly garbage
    } catch (Exception e) {
      String errMsg = e.getMessage().length() > 150 ? e.getMessage().substring(
          0, 150) : e.getMessage();
      System.err.println(errMsg.replace("\n", "") + " >>> URL was: \""
          + realUrl + "\"");
    }
    return null;
  }

  /**
   * @return an opened stream.
   */
  public static InputStream getConnection(String realUrl) throws IOException {
    URL url = new URL(realUrl);
    // maybe we need to write our http connection. DNS cache and
    // inputstream reader...
    /*
     * get the IP of the DNS, put it into the cache. Then open an
     * socketinputstream with an 16k long buffer and let the reader read it...
     */
    URLConnection con = url.openConnection();
    // con.setConnectTimeout(50);
    con.addRequestProperty(USER_AGENT_KEY, USER_AGENT);
    return con.getInputStream();
  }

  /**
   * Filters outlinks from a parsed page that NOT matches the given matcher.
   */
  public static HashSet<String> filter(HashSet<String> set, Pattern matcher) {
    if (matcher != null) {
      Iterator<String> iterator = set.iterator();
      while (iterator.hasNext()) {
        if (!matcher.matcher(iterator.next()).matches()) {
          iterator.remove();
        }
      }
    }
    return set;
  }

  /**
   * Method to extract outlinks of a given HTML doc in string.
   */
  public static HashSet<String> extractOutlinks(String in, String url)
      throws ParserException {

    final String baseUrl = extractBaseUrl(url);
    if (baseUrl == null)
      return null;

    final HashSet<String> set = new HashSet<>();
    Parser parser = new Parser(in);
    NodeList matches = parser.extractAllNodesThatMatch(LINK_FILTER);
    SimpleNodeIterator it = matches.elements();
    while (it.hasMoreNodes()) {
      LinkTag node = (LinkTag) it.nextNode();
      String link = node.getLink();
      if (link != null && !link.isEmpty()) {
        if (isValid(link)) {
          set.add(link);
        } else {
          // retry by expanding relative links
          if (link.charAt(0) == '/') {
            link = baseUrl + link;
            if (isValid(link)) {
              set.add(link);
            }
          }
        }
      }
    }
    return set;
  }

  /**
   * Consumes a given {@link InputStream} and returns a string consisting of the
   * html code of the site.
   */
  public static String consumeStream(InputStream stream) throws IOException {
    UniversalDetector detector = new UniversalDetector(null);
    // websites approach 1mb, so allocate that space
    byte[] dest = new byte[1024 * 1024];
    int offset = 0;
    try (BufferedInputStream inputStream = new BufferedInputStream(stream,
        BUFFER_SIZE)) {
      byte[] buf = new byte[BUFFER_SIZE];
      int length = -1;
      while ((length = inputStream.read(buf)) != -1) {
        detector.handleData(buf, 0, length);
        if (offset + length > dest.length) {
          byte[] tmpDest = new byte[dest.length * 2];
          System.arraycopy(dest, 0, tmpDest, 0, offset);
          dest = tmpDest;
        }
        System.arraycopy(buf, 0, dest, offset, length);
        offset += length;
      }
      detector.dataEnd();
    }
    String encoding = detector.getDetectedCharset();
    return new String(dest, 0, offset, encoding);
  }

  /**
   * Extracts a base url from the given url (to make relative outlinks to
   * absolute ones).
   * 
   * @return a base url or null if none was found.
   */
  public static String extractBaseUrl(String url) {
    Matcher matcher = BASE_URL.matcher(url);
    if (matcher.find()) {
      return matcher.group();
    }
    return null;
  }

  /**
   * Checks if the site does not end with unparsable suffixes likes PDF and if
   * its a valid url by extracting a base url at at index 0.
   */
  public static boolean isValid(final String s) {
    Matcher baseMatcher = BASE_URL.matcher(s);
    return baseMatcher.find() && baseMatcher.start() == 0
        && !IGNORE_SUFFIX_PATTERN.matcher(s).matches();
  }

}
