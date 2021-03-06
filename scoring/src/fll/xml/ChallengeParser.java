/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import net.mtu.eggplant.xml.NodelistElementCollectionAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import fll.Utilities;
import fll.db.GenerateDB;
import fll.util.FP;
import fll.util.LogUtils;

/**
 * Parse challenge description and generate script/text for scoreEntry page.
 */
public final class ChallengeParser {

  /**
   * The expected namespace for FLL documents
   */
  public static final String FLL_NAMESPACE = "http://www.hightechkids.org";

  private static final Logger LOG = LogUtils.getLogger();

  /**
   * Parse the specified XML document and report errors.
   * <ul>
   * <li>arg[0] - the location of the document to parse
   * </ul>
   */
  public static void main(final String[] args) {
    LogUtils.initializeLogging();

    if (args.length < 1) {
      LOG.fatal("Usage: ChallengeParser <xml file>");
      System.exit(1);
    }
    final File challengeFile = new File(args[0]);
    if (!challengeFile.exists()) {
      LOG.fatal(challengeFile.getAbsolutePath()
          + " doesn't exist");
      System.exit(1);
    }
    if (!challengeFile.canRead()) {
      LOG.fatal(challengeFile.getAbsolutePath()
          + " is not readable");
      System.exit(1);
    }
    if (!challengeFile.isFile()) {
      LOG.fatal(challengeFile.getAbsolutePath()
          + " is not a file");
      System.exit(1);
    }
    try {
      final Reader input = new InputStreamReader(new FileInputStream(challengeFile), Utilities.DEFAULT_CHARSET);
      final Document challengeDocument = ChallengeParser.parse(input);
      if (null == challengeDocument) {
        LOG.fatal("Error parsing challenge descriptor");
        System.exit(1);
      }

      final ChallengeDescription description = new ChallengeDescription(challengeDocument.getDocumentElement());

      LOG.info("Title: "
          + description.getTitle());
    } catch (final Exception e) {
      LOG.fatal(e, e);
      System.exit(1);
    }
  }

  /**
   * Used to compare the initial value against enum values and min/maxes.
   */
  public static final double INITIAL_VALUE_TOLERANCE = 1E-4;

  private ChallengeParser() {
    // no instances
  }

  /**
   * Parse the challenge document from the given stream. The document will be
   * validated and must be in the fll namespace. Does not close the stream after
   * reading.
   * 
   * @param stream a stream containing document
   * @return the challengeDocument, null on an error
   */
  public static Document parse(final Reader stream) {
    try {
      final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      final Source schemaFile = new StreamSource(classLoader.getResourceAsStream("fll/resources/fll.xsd"));
      final Schema schema = factory.newSchema(schemaFile);

      final Document document = XMLUtils.parse(stream, schema);

      // challenge descriptor specific checks
      validateDocument(document);

      return document;
    } catch (final SAXParseException spe) {
      throw new RuntimeException(
                                 String.format("Error parsing file line: %d column: %d%n Message: %s%n This may be caused by using the wrong version of the software or an improperly formatted challenge descriptor or attempting to parse a file that is not a challenge descriptor.",
                                               spe.getLineNumber(), spe.getColumnNumber(), spe.getMessage()), spe);
    } catch (final SAXException se) {
      throw new RuntimeException(
                                 "The challenge descriptor was found to be invalid, check that you are parsing a challenge descriptor file and not something else",
                                 se);
    } catch (final ParseException pe) {
      throw new RuntimeException(pe);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Do validation of the document that cannot be done by the XML parser.
   * 
   * @param document the document to validate
   * @throws ParseException
   * @throws RuntimeException if an error occurs
   */
  private static void validateDocument(final Document document) throws ParseException {
    final Element rootElement = document.getDocumentElement();
    if (!"fll".equals(rootElement.getTagName())) {
      throw new RuntimeException("Not a fll challenge description file");
    }

    for (final Element childNode : new NodelistElementCollectionAdapter(rootElement.getChildNodes())) {
      if ("Performance".equals(childNode.getNodeName())
          || "SubjectiveCategory".equals(childNode.getNodeName())) {
        final Element childElement = childNode;

        // get all nodes named goal at any level under category element
        final Map<String, Element> goals = new HashMap<String, Element>();
        for (final Element element : new NodelistElementCollectionAdapter(childElement.getElementsByTagName("goal"))) {
          final String name = element.getAttribute("name");
          goals.put(name, element);

          // check initial values
          final double initialValue = Utilities.NUMBER_FORMAT_INSTANCE.parse(element.getAttribute("initialValue"))
                                                                      .doubleValue();
          if (XMLUtils.isEnumeratedGoal(element)) {
            boolean foundMatch = false;
            for (final Element valueEle : new NodelistElementCollectionAdapter(element.getChildNodes())) {
              final double score = Utilities.NUMBER_FORMAT_INSTANCE.parse(valueEle.getAttribute("score")).doubleValue();
              if (FP.equals(score, initialValue, INITIAL_VALUE_TOLERANCE)) {
                foundMatch = true;
              }
            }
            if (!foundMatch) {
              throw new RuntimeException(
                                         String.format("Initial value for %s(%f) does not match the score of any value element within the goal",
                                                       name, initialValue));
            }

          } else {
            final double min = Utilities.NUMBER_FORMAT_INSTANCE.parse(element.getAttribute("min")).doubleValue();
            final double max = Utilities.NUMBER_FORMAT_INSTANCE.parse(element.getAttribute("max")).doubleValue();
            if (FP.lessThan(initialValue, min, INITIAL_VALUE_TOLERANCE)) {
              throw new RuntimeException(String.format("Initial value for %s(%f) is less than min(%f)", name,
                                                       initialValue, min));
            }
            if (FP.greaterThan(initialValue, max, INITIAL_VALUE_TOLERANCE)) {
              throw new RuntimeException(String.format("Initial value for %s(%f) is greater than max(%f)", name,
                                                       initialValue, max));
            }
          }
        }

        // for all computedGoals
        for (final Element computedGoalElement : new NodelistElementCollectionAdapter(
                                                                                      childElement.getElementsByTagName("computedGoal"))) {

          // for all termElements
          for (final Element termElement : new NodelistElementCollectionAdapter(
                                                                                computedGoalElement.getElementsByTagName("term"))) {

            // check that the computed goal only references goals
            final String referencedGoalName = termElement.getAttribute("goal");
            if (!goals.containsKey(referencedGoalName)) {
              throw new RuntimeException("Computed goal '"
                  + computedGoalElement.getAttribute("name") + "' references goal '" + referencedGoalName
                  + "' which is not a standard goal");
            }
          }

          // for all goalRef elements
          for (final Element goalRefElement : new NodelistElementCollectionAdapter(
                                                                                   computedGoalElement.getElementsByTagName("goalRef"))) {

            // can't reference a non-enum goal with goalRef in enumCond
            final String referencedGoalName = goalRefElement.getAttribute("goal");
            final Element referencedGoalElement = goals.get(referencedGoalName);
            if (!XMLUtils.isEnumeratedGoal(referencedGoalElement)) {
              throw new RuntimeException("Computed goal '"
                  + computedGoalElement.getAttribute("name") + "' has a goalRef element that references goal '"
                  + referencedGoalName + " " + referencedGoalElement + "' which is not an enumerated goal");
            }
          }

        } // end foreach computed goal

        // for all terms
        for (final Element termElement : new NodelistElementCollectionAdapter(childElement.getElementsByTagName("term"))) {
          final String goalValueType = termElement.getAttribute("scoreType");
          final String referencedGoalName = termElement.getAttribute("goal");
          final Element referencedGoalElement = goals.get(referencedGoalName);
          // can't use the raw score of an enum inside a polynomial term
          if ("raw".equals(goalValueType)
              && (XMLUtils.isEnumeratedGoal(referencedGoalElement) || XMLUtils.isComputedGoal(referencedGoalElement))) {
            throw new RuntimeException(
                                       "Cannot use the raw score from an enumerated or computed goal in a polynomial term.  Referenced goal '"
                                           + referencedGoalName + "'");
          }
        }

      } // end if child node (performance or subjective)
    } // end foreach child node
  } // end validateDocument

  public static String compareStructure(final Document curDoc,
                                        final Document newDoc) {
    return compareStructure(new ChallengeDescription(curDoc.getDocumentElement()),
                            new ChallengeDescription(newDoc.getDocumentElement()));
  }

  /**
   * If the new document differs from the current document in a way that the
   * database structure will be modified.
   * 
   * @param curDoc the current document
   * @param newDoc the document to check against
   * @return null if everything checks out OK, otherwise the error message
   */
  public static String compareStructure(final ChallengeDescription curDoc,
                                        final ChallengeDescription newDoc) {
    final PerformanceScoreCategory curPerfElement = curDoc.getPerformance();
    final PerformanceScoreCategory newPerfElement = newDoc.getPerformance();

    final Map<String, String> curPerGoals = gatherColumnDefinitions(curPerfElement);
    final Map<String, String> newPerGoals = gatherColumnDefinitions(newPerfElement);
    final String goalCompareMessage = compareGoalDefinitions("Performance", curPerGoals, newPerGoals);
    if (null != goalCompareMessage) {
      return goalCompareMessage;
    }

    final List<ScoreCategory> curSubCats = curDoc.getSubjectiveCategories();
    final List<ScoreCategory> newSubCats = newDoc.getSubjectiveCategories();
    if (curSubCats.size() != newSubCats.size()) {
      return "New document has "
          + newSubCats.size() + " subjective categories, current document has " + curSubCats.size()
          + " subjective categories";
    }
    final Map<String, Map<String, String>> curCats = new HashMap<String, Map<String, String>>();
    for (final ScoreCategory ele : curSubCats) {
      final String name = ele.getName();
      final Map<String, String> goalDefs = gatherColumnDefinitions(ele);
      curCats.put(name, goalDefs);
    }

    for (final ScoreCategory ele : newSubCats) {
      final String name = ele.getName();
      if (!curCats.containsKey(name)) {
        return "New document has subjective category '"
            + name + "' which is not in the current document";
      }

      final Map<String, String> curGoalDefs = curCats.get(name);
      final Map<String, String> newGoalDefs = gatherColumnDefinitions(ele);
      final String goalMsg = compareGoalDefinitions(name, curGoalDefs, newGoalDefs);
      if (null != goalMsg) {
        return goalMsg;
      }
    }

    return null;
  }

  private static String compareGoalDefinitions(final String category,
                                               final Map<String, String> curGoals,
                                               final Map<String, String> newGoals) {
    if (curGoals.size() != newGoals.size()) {
      return "New document has "
          + newGoals.size() + " goals in category '" + category + "', current document has " + curGoals.size()
          + " goals";
    }

    for (final Map.Entry<String, String> curEntry : curGoals.entrySet()) {
      if (!newGoals.containsKey(curEntry.getKey())) {
        return "New document is missing goal '"
            + curEntry.getKey() + "'";
      }
      final String curDef = curEntry.getValue();
      final String newDef = newGoals.get(curEntry.getKey());
      if (!curDef.equals(newDef)) {
        return "Database definition for goal '"
            + curEntry.getKey() + "' in category '" + category
            + "' is different in the new document from the current document" + " '" + curDef + "' vs '" + newDef + "'";
      }
    }
    return null;
  }

  /**
   * Get the column definitions for all goals in the specified element
   */
  private static Map<String, String> gatherColumnDefinitions(final ScoreCategory element) {
    final Map<String, String> goalDefs = new HashMap<String, String>();

    for (final AbstractGoal goal : element.getGoals()) {
      if (!goal.isComputed()) {
        final String columnDefinition = GenerateDB.generateGoalColumnDefinition(goal);
        final String goalName = goal.getName();
        goalDefs.put(goalName, columnDefinition);
      }
    }

    return goalDefs;
  }

}
