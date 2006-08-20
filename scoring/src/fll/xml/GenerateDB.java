/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.xml;

import fll.Queries;
import fll.Team;
import fll.Utilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Collection;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Generate tables for fll tournament from XML document
 *
 * @version $Revision$
 */
public final class GenerateDB {

  private static final Logger LOG = Logger.getLogger(GenerateDB.class);
  /**
   * sql datatype for tournament columns.  Until we change the tournament
   * references to be all integers, let's just use a constant to make it easy
   * to change the size.
   */
  private static final String TOURNAMENT_DATATYPE = "varchar(128)";

  private static final String LOCATION_DATATYPE;
  private static final String BOOLEAN_DATATYPE;
  private static final String PARAM_VALUE_DATATYPE;
  public static final String TEXT_DATATYPE;
  
  static {
    TEXT_DATATYPE = "longvarchar";
    LOCATION_DATATYPE = TEXT_DATATYPE;
    BOOLEAN_DATATYPE = "boolean";
    PARAM_VALUE_DATATYPE = TEXT_DATATYPE;
  }
    
  /**
   * Generate a new database
   *
   * @param args ignored
   */
  public static void main(final String[] args) {
    try {
      if(args.length != 3) {
        System.err.println("You must specify <hostname> <root user> <root password>");
        System.exit(1);
      } else {
        final ClassLoader classLoader = ChallengeParser.class.getClassLoader();
        final Document challengeDocument = ChallengeParser.parse(classLoader.getResourceAsStream("resources/challenge-state-2005.xml"));
        //final String db = "tomcat/webapps/fll-sw/WEB-INF/fll";
        final String db = "fll";
        generateDB(challengeDocument, db, true);

        final Connection connection = Utilities.createDBConnection("fll", "fll", db);
        final Document document = Queries.getChallengeDocument(connection);
        System.out.println("Title: " + document.getDocumentElement().getAttribute("title"));
        connection.close();
      }
    } catch(final Exception e) {
      e.printStackTrace();
    }
  }
  
  private GenerateDB() {
     
  }

  /**
   * Generate a completly new DB from document.  This also stores the document
   * in the database for later use.
   *
   * @param document and XML document that describes a tournament
   * @param database name for the database to generate
   * @param forceRebuild recreate all tables from scratch, if false don't
   * recreate the tables that hold team information
   */
  public static void generateDB(final Document document,
                                final String database,
                                final boolean forceRebuild)
    throws SQLException, UnsupportedEncodingException {
    Connection connection = null;
    Statement stmt = null;
    PreparedStatement prep = null;
    ResultSet rs = null;
    try {
      connection = Utilities.createDBConnection("fll", "fll", database);
      
      stmt = connection.createStatement();

      stmt.executeUpdate("SET WRITE_DELAY 100 MILLIS");

      // get list of tables that already exist
      final DatabaseMetaData metadata = connection.getMetaData();
      rs = metadata.getTables(null, null, "%", null);
      final Collection<String> tables = new LinkedList<String>();
      while(rs.next()) {
        tables.add(rs.getString(3).toLowerCase());
      }
      rs.close();
      if(LOG.isDebugEnabled()) {
        LOG.debug("Tables:" + tables);
      }
      
      //Table structure for table 'Tournaments'
      if(forceRebuild) {
        stmt.executeUpdate("DROP TABLE IF EXISTS Tournaments");
      }

      if(forceRebuild || !tables.contains("Tournaments".toLowerCase())) {
        stmt.executeUpdate("CREATE TABLE Tournaments ("
                           + "Name " + TOURNAMENT_DATATYPE + " NOT NULL,"
                           + "Location " + LOCATION_DATATYPE + ","
                           + "NextTournament " + TOURNAMENT_DATATYPE + " default NULL," //Tournament that teams may advance to from this one
                           + "PRIMARY KEY (Name)"
                           +")");
        stmt.executeUpdate("INSERT INTO Tournaments (Name, Location) VALUES ('DUMMY', 'Default dummy tournament')");
        stmt.executeUpdate("INSERT INTO Tournaments (Name, Location) VALUES ('DROP', 'Dummy tournament for teams that drop out')");
      }
      
      // table to hold head-to-head playoff meta-data
      stmt.executeUpdate("DROP TABLE IF EXISTS PlayoffData");
      stmt.executeUpdate("CREATE TABLE PlayoffData (" +
            " Division varchar(32) NOT NULL," +
            " Tournament " + TOURNAMENT_DATATYPE + " NOT NULL," +
            " PlayoffRound integer NOT NULL," +
            " LineNumber integer NOT NULL," +
            " Team integer default " + Team.NULL_TEAM_NUMBER + "," +
            " AssignedTable varchar(64) default NULL," +
            " PRIMARY KEY (Division, Tournament, PlayoffRound, LineNumber)" +
            ")");

      // Table structure for table 'Teams'
      if(forceRebuild) {
        stmt.executeUpdate("DROP TABLE IF EXISTS Teams");
      }
      if(forceRebuild || !tables.contains("Teams".toLowerCase())) {
        stmt.executeUpdate("CREATE TABLE Teams ("
                           + "  TeamNumber integer NOT NULL,"
                           + "  TeamName varchar(255) default '<No Name>' NOT NULL,"
                           + "  Organization varchar(255),"
                           + "  Division varchar(32) default '1' NOT NULL,"
                           + "  Region varchar(255) default 'DUMMY' NOT NULL,"
                           + "  PRIMARY KEY  (TeamNumber)"
                           + ")");
      }

      // table to hold team numbers of teams in this tournament
      if(forceRebuild) {
        stmt.executeUpdate("DROP TABLE IF EXISTS TournamentTeams");
      }
      if(forceRebuild || !tables.contains("TournamentTeams".toLowerCase())) {
        stmt.executeUpdate("CREATE TABLE TournamentTeams ("
                           + "  TeamNumber integer NOT NULL,"
                           + "  Tournament " + TOURNAMENT_DATATYPE + " NOT NULL,"
                           + "  advanced " + BOOLEAN_DATATYPE + " default 0 NOT NULL,"
                           + "  PRIMARY KEY (TeamNumber, Tournament)"
                           + ")");
      }

      // Table structure for table 'TournamentParameters'
      if(forceRebuild) {
        stmt.executeUpdate("DROP TABLE IF EXISTS TournamentParameters");
      }
      if(forceRebuild || !tables.contains("TournamentParameters".toLowerCase())) {
        stmt.executeUpdate("CREATE TABLE TournamentParameters ("
                           + "  Param varchar(64) NOT NULL,"
                           + "  Value " + PARAM_VALUE_DATATYPE + " NOT NULL,"
                           + "  Description varchar(255) default NULL,"
                           + "  PRIMARY KEY  (Param)"
                           + ")");
        
        //populate tournament parameters with default values
        stmt.executeUpdate("INSERT INTO TournamentParameters (Param, Value, Description) VALUES ('CurrentTournament', 'DUMMY', 'This is the currently running tournament name - see Tournaments table')");
        stmt.executeUpdate("INSERT INTO TournamentParameters (Param, Value, Description) VALUES ('SeedingRounds', 3, 'Number of seeding rounds before elimination round - used to downselect top performance scores in queries')");
        stmt.executeUpdate("INSERT INTO TournamentParameters (Param, Value, Description) VALUES ('StandardizedMean', 100, 'Standard mean for computing the standardized scores')");
        stmt.executeUpdate("INSERT INTO TournamentParameters (Param, Value, Description) VALUES ('StandardizedSigma', 20, 'Standard deviation for computing the standardized scores')");

        prep = connection.prepareStatement("INSERT INTO TournamentParameters (Param, Value, Description) VALUES ('ChallengeDocument', ?, 'The XML document describing the challenge')");
      } else {
        prep = connection.prepareStatement("UPDATE TournamentParameters SET Value = ? WHERE Param = 'ChallengeDocument'");
      }        
      
      //dump the document into a byte array so we can push it into the database
      final XMLWriter xmlwriter = new XMLWriter();
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      xmlwriter.setOutput(baos, "UTF8");
      xmlwriter.write(document);
      final byte[] bytes = baos.toByteArray();
      final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
      prep.setAsciiStream(1, bais, bytes.length);
      prep.executeUpdate();
      
      // Table structure for table 'Judges'
      stmt.executeUpdate("DROP TABLE IF EXISTS Judges");
      stmt.executeUpdate("CREATE TABLE Judges ("
                         + "  id varchar(64) NOT NULL,"
                         + "  category varchar(64) NOT NULL,"
                         + "  Tournament " + TOURNAMENT_DATATYPE + " NOT NULL,"
                         + "  Division varchar(32) NOT NULL,"
                         + "  PRIMARY KEY  (id,category,Tournament,Division)"
                         + ")");

      
      // Table structure for table 'tablenames'
      stmt.executeUpdate("DROP TABLE IF EXISTS tablenames");
      stmt.executeUpdate("CREATE TABLE tablenames ("
                         + "  Tournament varchar(64) NOT NULL,"
                         + "  SideA varchar(64) NOT NULL,"
                         + "  SideB varchar(64) NOT NULL,"
                         + "  PRIMARY KEY (Tournament,SideA,SideB)"
                         + ")");

      
      final Element rootElement = document.getDocumentElement();
      final StringBuilder createStatement = new StringBuilder();
      
      //performance
      {
        final Element performanceElement = (Element)rootElement.getElementsByTagName("Performance").item(0);
        final String tableName = "Performance";
        stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
        createStatement.append("CREATE TABLE " + tableName + " (");
        createStatement.append(" TeamNumber INTEGER NOT NULL,");
        createStatement.append(" Tournament " + TOURNAMENT_DATATYPE + " NOT NULL,");
        createStatement.append(" RunNumber INTEGER NOT NULL,");
        createStatement.append(" TimeStamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,");
        createStatement.append(" NoShow " + BOOLEAN_DATATYPE + " DEFAULT 0 NOT NULL,");
        createStatement.append(" Bye " + BOOLEAN_DATATYPE + " DEFAULT 0 NOT NULL,");
        final NodeList goals = performanceElement.getElementsByTagName("goal");
        for(int i=0; i<goals.getLength(); i++) {
          final Element element = (Element)goals.item(i);
          final String columnDefinition = generateGoalColumnDefinition(element);
          createStatement.append(" " + columnDefinition + ",");
        }
        createStatement.append(" ComputedTotal INTEGER DEFAULT NULL,");
        createStatement.append(" StandardizedScore float default NULL,");
        createStatement.append(" PRIMARY KEY (TeamNumber, Tournament, RunNumber)");
        createStatement.append(");");
        stmt.executeUpdate(createStatement.toString());

        // create view for max seeding round score
        stmt.executeUpdate("CREATE VIEW performance_seeding_max AS SELECT TeamNumber, Tournament, Max(ComputedTotal) As Score FROM Performance WHERE NoShow = 0 AND RunNumber <= (SELECT Value FROM TournamentParameters WHERE TournamentParameters.Param = 'SeedingRounds') GROUP BY TeamNumber, Tournament");
      }
      
      //loop over each subjective category
      final StringBuilder finalScores = new StringBuilder();
      finalScores.append("CREATE TABLE FinalScores (");
      finalScores.append("TeamNumber integer NOT NULL,");
      finalScores.append("Tournament " + TOURNAMENT_DATATYPE + " NOT NULL,");
      final NodeList subjectiveCategories = rootElement.getElementsByTagName("subjectiveCategory");
      for(int cat=0; cat<subjectiveCategories.getLength(); cat++) {
        createStatement.setLength(0);

        final Element categoryElement = (Element)subjectiveCategories.item(cat);
        final String tableName = categoryElement.getAttribute("name");

        stmt.executeUpdate("DROP TABLE IF EXISTS " + tableName);
        createStatement.append("CREATE TABLE " + tableName + " (");
        createStatement.append(" TeamNumber INTEGER NOT NULL,");
        createStatement.append(" Tournament " + TOURNAMENT_DATATYPE + " NOT NULL,");
        createStatement.append(" Judge VARCHAR(64) NOT NULL,");
        createStatement.append(" NoShow " + BOOLEAN_DATATYPE + " DEFAULT 0 NOT NULL,");
        final NodeList goals = categoryElement.getElementsByTagName("goal");
        for(int i=0; i<goals.getLength(); i++) {
          final Element element = (Element)goals.item(i);
          final String columnDefinition = generateGoalColumnDefinition(element);
          createStatement.append(" " + columnDefinition + ",");
        }
        createStatement.append(" ComputedTotal INTEGER DEFAULT NULL,");
        createStatement.append(" StandardizedScore float default NULL,");
        createStatement.append(" PRIMARY KEY (TeamNumber, Tournament, Judge)");
        createStatement.append(");");
        stmt.executeUpdate(createStatement.toString());

        finalScores.append(tableName + " float default NULL,");
      }

      //Table structure for table 'FinalScores'
      finalScores.append(" performance float default NULL,");
      finalScores.append(" OverallScore float,");
      finalScores.append("PRIMARY KEY (TeamNumber, Tournament)");
      finalScores.append(")");
      stmt.executeUpdate("DROP TABLE IF EXISTS FinalScores");
      LOG.info(finalScores.toString());
      stmt.executeUpdate(finalScores.toString());


      //FIX add foreign key constraints
      
    } finally {
      Utilities.closeStatement(stmt);
      Utilities.closePreparedStatement(prep);
      Utilities.closeConnection(connection);
    }

  }

  /**
   * Generate the definition of a column for the given goal element.
   *
   * @param goalElement element that represents the goal
   * @return the column definition
   */
  private static String generateGoalColumnDefinition(final Element goalElement) {
    final String goalName = goalElement.getAttribute("name");

    //check if there are any subelements to determine if this goal is enumerated or not

    String definition = goalName;
    final NodeList posValues = goalElement.getElementsByTagName("value");
    if(posValues.getLength() > 0) {
      //enumerated
      //HSQLDB doesn't support enum
      definition += " longvarchar";
    } else {
      definition += " INTEGER";
    }
    
    LOG.debug("GoalColumnDefinition: " + definition);
    
    return definition;
  }

}