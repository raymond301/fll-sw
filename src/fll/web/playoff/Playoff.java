/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.playoff;

import fll.Queries;
import fll.Team;
import fll.Utilities;

import fll.xml.ChallengeParser;

import java.io.IOException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.text.ParseException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.jsp.JspWriter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Add class comment here!
 *
 * @version $Revision$
 */
public final class Playoff {

  /**
   * Just for debugging.
   *
   * @param args ignored
   */
  public static void main(final String[] args) {
    try {
      final ClassLoader classLoader = ChallengeParser.class.getClassLoader();
      final Connection connection = Utilities.createDBConnection("netserver", "fll", "fll", "fll");
      final Document challengeDocument = Queries.getChallengeDocument(connection);
      final Team a = new Team();
      a.setTeamNumber(1);
      final Team b = new Team();
      b.setTeamNumber(3);
      final int runNumber = 3;
      final Team winner = pickWinner(connection, challengeDocument, a, b, runNumber);
      System.out.println("winner: " + winner.getTeamNumber());
      
    } catch(final Exception e) {
      e.printStackTrace();
    }
  }
  
  private Playoff() {
     
  }

  /**
   * Build the list of teams ordered from top to bottom (visually) of a single
   * elimination bracket.
   *
   * @param connection connection to the database
   * @param divisionStr the division to generate brackets for, as a String
   * @param tournamentTeams keyed by team number
   * @return a List of teams
   * @throws SQLException on a database error
   */
  public static List buildInitialBracketOrder(final Connection connection,
                                              final String divisionStr,
                                              final Map tournamentTeams)
    throws SQLException {

    final List seedingOrder = Queries.getPlayoffSeedingOrder(connection, divisionStr, tournamentTeams);
    if(seedingOrder.size() > 128) {
      //TODO one of these days I need to compute this rather than using an array
      throw new RuntimeException("More than 128 teams sent to playoff brackets!  System overload");
    }
    int bracketIndex = 0;
    while(SEED_ARRAY[bracketIndex].length < seedingOrder.size()) {
      bracketIndex++;
    }
    
    final List list = new LinkedList();
    for(int i=0; i<SEED_ARRAY[bracketIndex].length; i++) {
      if(SEED_ARRAY[bracketIndex][i] > seedingOrder.size()) {
        list.add(Team.BYE);
      } else {
        final Team team = (Team)seedingOrder.get(SEED_ARRAY[bracketIndex][i]-1);
        list.add(team);
      }
    }
    return list;
  }
  
  /**
   * Decide who is the winner of runNumber.  Calls Queries.updateScoreTotals()
   * to ensure the ComputedScore column is up to date
   *
   * @param connection database connection with write access to Performance table
   * @param document XML document description of tournament
   * @param teamA first team to check
   * @param teamB second team to check
   * @param runNumber what run to compare scores for
   * @return the team that is the winner.  Team.TIE is returned in the case of a
   * tie and null when the scores have not yet been entered
   * @see Team#TIE
   * @see fll.Queries#updateScoreTotals(Document, Connection)
   * @throws SQLException on a database error
   * @throws ParseException if the XML document is invalid
   */
  public static Team pickWinner(final Connection connection,
                                final Document document,
                                final Team teamA,
                                final Team teamB,
                                final int runNumber)
    throws SQLException, ParseException {
    final String tournament = Queries.getCurrentTournament(connection);
    
    if(Team.BYE.equals(teamA)) {
      if(!performanceScoreExists(connection, teamB, runNumber)) {
        insertBye(connection, teamB, runNumber);
      }
      return teamB;
    } else if(Team.BYE.equals(teamB)) {
      if(!performanceScoreExists(connection, teamA, runNumber)) {
        insertBye(connection, teamA, runNumber);
      }
      return teamA;
    } else if(Team.TIE.equals(teamA) || Team.TIE.equals(teamB)) {
      return null;
    } else {
      //make sure scores are up to date
      Queries.updateScoreTotals(document, connection);
      
      if(performanceScoreExists(connection, teamA, runNumber)
         && performanceScoreExists(connection, teamB, runNumber)) {
        final boolean noshowA = isNoShow(connection, tournament, teamA, runNumber);
        final boolean noshowB = isNoShow(connection, tournament, teamB, runNumber);
        if(noshowA && !noshowB) {
          return teamB;
        } else if(!noshowA && noshowB) {
          return teamA;
        } else {
          final int scoreA = getPerformanceScore(connection, tournament, teamA, runNumber); 
          final int scoreB = getPerformanceScore(connection, tournament, teamB, runNumber);
          if(scoreA < scoreB) {
            return teamB;
          } else if(scoreB < scoreA) {
            return teamA;
          } else {
            final Element performanceElement = (Element)document.getDocumentElement().getElementsByTagName("Performance").item(0);
            final Element tiebreakerElement = (Element)performanceElement.getElementsByTagName("tiebreaker").item(0);
            final NodeList goals = performanceElement.getElementsByTagName("goal");
              
            Statement stmtA = null;
            Statement stmtB = null;
            ResultSet rsA = null;
            ResultSet rsB = null;
            try {
              stmtA = connection.createStatement();
              stmtB = connection.createStatement();
              rsA = stmtA.executeQuery("SELECT * FROM Performance WHERE Tournament = '" + tournament + "' AND RunNumber = " + runNumber + " and TeamNumber = " + teamA.getTeamNumber());
              rsB = stmtB.executeQuery("SELECT * FROM Performance WHERE Tournament = '" + tournament + "' AND RunNumber = " + runNumber + " and TeamNumber = " + teamB.getTeamNumber());
              if(rsA.next() && rsB.next()) {
                //walk test elements in tiebreaker to decide who wins
                Node child = tiebreakerElement.getFirstChild();
                while(null != child) {
                  if(child instanceof Element) {
                    final Element element = (Element)child;
                    if("test".equals(element.getTagName())) {
                      final String goalName = element.getAttribute("goal");
                      final Element goalDefinition = findGoalDefinition(goals, goalName);
                      
                      final int multiplier = Utilities.NUMBER_FORMAT_INSTANCE.parse(goalDefinition.getAttribute("multiplier")).intValue();
                      final NodeList values = goalDefinition.getElementsByTagName("value");
                      int valueA = -1;
                      int valueB = -1;
                      if(values.getLength() == 0) {
                        valueA = rsA.getInt(goalName);
                        valueB = rsB.getInt(goalName);
                      } else {
                        //enumerated
                        final String enumA = rsA.getString(goalName);
                        final String enumB = rsB.getString(goalName);
                        boolean foundA = false;
                        boolean foundB = false;
                        for(int v=0; v<values.getLength() && (!foundA || !foundB); v++) {
                          final Element value = (Element)values.item(v);
                          final String enumValue = value.getAttribute("value");
                          final int enumScore = Utilities.NUMBER_FORMAT_INSTANCE.parse(value.getAttribute("score")).intValue() * multiplier;
                          if(enumValue.equals(enumA)) {
                            valueA = enumScore;
                            foundA = true;
                          }
                          if(enumValue.equals(enumB)) {
                            valueB = enumScore;
                            foundB = true;
                          }
                        }
                        if(!foundA || !foundB) {
                          throw new RuntimeException("Error, enum value in database for goal: " + goalName + " is not a valid value."
                                                     + " foundA: " + foundA
                                                     + " foundB: " + foundB
                                                     );
                        }
                        
                      }
                      
                      final String highlow = element.getAttribute("winner");
                      if(valueA > valueB) {
                        return ("high".equals(highlow) ? teamA : teamB);
                      } else if(valueA < valueB) {
                        return ("high".equals(highlow) ? teamB : teamA);
                      }
                    }
                  }
                  child = child.getNextSibling();
                }
              } else {
                throw new RuntimeException("Missing performance scores for teams: " + teamA.getTeamNumber() + " or " + teamB.getTeamNumber() + " for run: " + runNumber + " tournament: " + tournament);
              }
            } finally {
              Utilities.closeResultSet(rsA);
              Utilities.closeResultSet(rsB);
              Utilities.closeStatement(stmtA);
              Utilities.closeStatement(stmtB);
            }
            return Team.TIE;
          }
        }
      } else {
        return null;
      }
    }
  }

  /**
   * Insert a by run for a given team, tournament, run number in the
   * performance table.
   *
   * @throws SQLException on a database error
   */
  public static void insertBye(final Connection connection,
                               final Team team,
                               final int runNumber)
    throws SQLException {
    final String tournament = Queries.getCurrentTournament(connection);
    Statement stmt = null;
    try {
      stmt = connection.createStatement();
      stmt.executeUpdate("INSERT INTO Performance(TeamNumber, Tournament, RunNumber, Bye) VALUES( " + team.getTeamNumber() + ", '" + tournament + "', " + runNumber + ", 1)");
    } finally {
      Utilities.closeStatement(stmt);
    }
  }

  /**
   * If team is not null, calls performanceScoreExists(connection,
   * team.getTeamNumber(), runNumber), otherwise returns false.
   * 
   * @see #performanceScoreExists(Connection, int, int)
   */
  public static boolean performanceScoreExists(final Connection connection,
                                               final Team team,
                                               final int runNumber)
    throws SQLException {
    if(null == team) {
      return false;
    } else {
      return performanceScoreExists(connection, team.getTeamNumber(), runNumber);
    }
  }
  
  /**
   * Test if a performance score exists for the given team, tournament and
   * run number
   *
   * @throws SQLException on a database error
   */
  public static boolean performanceScoreExists(final Connection connection,
                                               final int teamNumber,
                                               final int runNumber)
    throws SQLException {
    final String tournament = Queries.getCurrentTournament(connection);
    
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT ComputedTotal FROM Performance"
                             + " WHERE TeamNumber = " + teamNumber
                             + " AND Tournament = '" + tournament + "'"
                             + " AND RunNumber = " + runNumber);
      return rs.next();
    } finally {
      Utilities.closeResultSet(rs);
      Utilities.closeStatement(stmt);
    }
  }

  /**
   * Get the performance score for the given team, tournament and run number
   *
   * @throws SQLException on a database error
   * @throws IllegalArgumentException if no score exists
   */
  public static int getPerformanceScore(final Connection connection,
                                        final String tournament,
                                        final Team team,
                                        final int runNumber)
    throws SQLException, IllegalArgumentException {
    if(null == team) {
      throw new IllegalArgumentException("Cannot get score for null team");
    } else {
      Statement stmt = null;
      ResultSet rs = null;
      try {
        stmt = connection.createStatement();
        rs = stmt.executeQuery("SELECT ComputedTotal FROM Performance"
                               + " WHERE TeamNumber = " + team.getTeamNumber()
                               + " AND Tournament = '" + tournament + "'"
                               + " AND RunNumber = " + runNumber);
        if(rs.next()) {
          return rs.getInt(1);
        } else {
          throw new IllegalArgumentException("No score exists for tournament: " + tournament
                                             + " teamNumber: " + team.getTeamNumber()
                                             + " runNumber: " + runNumber);
        }
      } finally {
        Utilities.closeResultSet(rs);
        Utilities.closeStatement(stmt);
      }
    }
  }

  /**
   * Get the value of NoShow for the given team, tournament and run number
   *
   * @throws SQLException on a database error
   * @throws IllegalArgumentException if no score exists
   */
  public static boolean isNoShow(final Connection connection,
                                 final String tournament,
                                 final Team team,
                                 final int runNumber)
    throws SQLException, IllegalArgumentException {
    Statement stmt = null;
    ResultSet rs = null;
    try {
      stmt = connection.createStatement();
      rs = stmt.executeQuery("SELECT NoShow FROM Performance"
                             + " WHERE TeamNumber = " + team.getTeamNumber()
                             + " AND Tournament = '" + tournament + "'"
                             + " AND RunNumber = " + runNumber);
      if(rs.next()) {
        return rs.getBoolean(1);
      } else {
        throw new RuntimeException("No score exists for tournament: " + tournament
                                   + " teamNumber: " + team.getTeamNumber()
                                   + " runNumber: " + runNumber);
      }
    } finally {
      Utilities.closeResultSet(rs);
      Utilities.closeStatement(stmt);
    }
  }

  /**
   * Defaults showScore to true.
   * 
   * @see #getDisplayString(Connection, String, int, Team, boolean)
   */
  public static String getDisplayString(final Connection connection,
                                        final String currentTournament,
                                        final int runNumber,
                                        final Team team)
    throws IllegalArgumentException, SQLException {
    return getDisplayString(connection, currentTournament, runNumber, team, true);
  }
  
  /**
   * What to display given a team number, handles TIE, null and BYE
   *
   * @param connection connection to the database
   * @param currentTournament the current tournament
   * @param runNumber the current run, used to get the score
   * @param team team to get display string for
   * @param showScore if the score should be shown
   * @throws IllegalArgumentException if teamNumber is invalid
   * @throws SQLException on a database error
   */
  public static String getDisplayString(final Connection connection,
                                        final String currentTournament,
                                        final int runNumber,
                                        final Team team,
                                        final boolean showScore)
    throws IllegalArgumentException, SQLException {
    if(Team.BYE.equals(team)) {
      return "<font class='TeamName'>BYE</font>";
    } else if(Team.TIE.equals(team)) {
      return "<font class='TIE'>TIE</font>";
    } else if(null == team) {
      return "&nbsp;";
    } else {
      final StringBuffer sb = new StringBuffer();
      sb.append("<font class='TeamNumber'>#");
      sb.append(team.getTeamNumber());
      sb.append("</font>&nbsp;<font class='TeamName'>");
      sb.append(team.getTeamName());
      sb.append("</font>");
      if(showScore && performanceScoreExists(connection, team, runNumber)) {
        sb.append("<font class='TeamScore'>&nbsp;Score: ");
        if(isNoShow(connection, currentTournament, team, runNumber)) {
          sb.append("No Show");
        } else {
          //only display score if it's not a bye
          sb.append(getPerformanceScore(connection, currentTournament, team, runNumber));
        }          
        sb.append("</font>");
      }
      return sb.toString();
    }
  }

  /**
   * Find a goal element with the given name in the list of goals.
   *
   * @return the element, null on error
   */
  private static Element findGoalDefinition(final NodeList goals,
                                            final String name) {
    for(int i=0; i<goals.getLength(); i++) {
      final Element e = (Element)goals.item(i);
      if(name.equals(e.getAttribute("name"))) {
        return e;
      }
    }
    return null;
  }

  /**
   * Output the table for the printable brackets to out
   */
  public static void displayPrintableBrackets(final Connection connection,
                                              final Document challengeDocument,
                                              final String division,
                                              final JspWriter out)
    throws IOException, SQLException, ParseException {
  
    final Map tournamentTeams = Queries.getTournamentTeams(connection);
    final String currentTournament = Queries.getCurrentTournament(connection);
  
    final int numSeedingRounds = Queries.getNumSeedingRounds(connection);
  
    //initialize currentRound to contain a full bracket setup
    List tempCurrentRound = buildInitialBracketOrder(connection, division, tournamentTeams);
    if(tempCurrentRound.size() > 1) {
      out.println("<table align='center' width='100%' border='0' cellpadding='3' cellspacing='0'>");
      
      //compute number of runs needed to complete the playoffs
      out.println("<tr>");
      final int initialBracketSize = tempCurrentRound.size();
      int numRuns = 1;
      while(Math.pow(2, numRuns) < initialBracketSize) {
        out.println("<th colspan='2'>Playoff Round " + numRuns + "</th>");
        numRuns++;
      }
      out.println("<th colspan='2'>Playoff Round " + numRuns + "</th>");
      numRuns++;
      out.println("<th colspan='2'>Playoff Round " + numRuns + "</th>");
      out.println("</tr>");

      //the row at which the last team was displayed
      int[] lastTeam = new int[numRuns];
      Arrays.fill(lastTeam, 0);
      //the teams for a given round
      Iterator[] currentRoundTeams = new Iterator[numRuns];
      //the bracket that will be displayed next
      int[] bracketIndex = new int[numRuns];
      Arrays.fill(bracketIndex, 1);
      //the team that will be displayed next
      int[] teamIndex = new int[numRuns];
      Arrays.fill(teamIndex, 1);
      
      for(int tempRunNumber=0; tempRunNumber<numRuns; tempRunNumber++) {
        //save off the team order for later 
        currentRoundTeams[tempRunNumber] = tempCurrentRound.iterator();
    
        final List newCurrentRound = new LinkedList();
        final Iterator prevIter = tempCurrentRound.iterator();
        while(prevIter.hasNext()) {
          final Team teamA = (Team)prevIter.next();
          if(prevIter.hasNext()) {
            final Team teamB = (Team)prevIter.next();
            final Team winner = pickWinner(connection, challengeDocument, teamA, teamB, tempRunNumber+numSeedingRounds+1);
            newCurrentRound.add(winner);
          } else {
            //handle finals
            newCurrentRound.add(teamA);
          }
        }
        tempCurrentRound = newCurrentRound;
      }
  
      //Now create rows, 4 per initial bracket, two columns for each playoff round
      //row is 1 based
      //playoffRunNumber is 0 based for array indexing
      for(int row=1; row<=initialBracketSize*4; row++) {
        out.println("<tr>");
        for(int playoffRunNumber=0; playoffRunNumber<numRuns; playoffRunNumber++) {
          final boolean evenTeamIndex = (teamIndex[playoffRunNumber] % 2 == 0);
          if( (0 == lastTeam[playoffRunNumber] //haven't seen a team yet
               && ( (row == ((1 << (playoffRunNumber+2))/2)-1) // first team in a later bracket
                    || (row ==1 && playoffRunNumber == 0) )) // first team first round
              || ((row - lastTeam[playoffRunNumber]) == (1 << (playoffRunNumber+2))) ) { //later teams
            
            //keep track of where we last output a team
            lastTeam[playoffRunNumber] = row;
            //team information
            final Team team = (Team)currentRoundTeams[playoffRunNumber].next();
            out.println("<td class='Leaf' width='200'>");
            out.println(getDisplayString(connection, currentTournament, (playoffRunNumber+numSeedingRounds+1), team, false));
            
            out.println("</td>");

            if(evenTeamIndex) {
              //skip for bridge
              out.println("<!-- skip column for bridge -->");
            } else {
              //bridgetop
              out.println("<td class='BridgeTop' width='10'>&nbsp;</td>");
            }
            
            teamIndex[playoffRunNumber]++;
          } else if( (row - lastTeam[playoffRunNumber]) == 1
                     && 0 != lastTeam[playoffRunNumber]) {
            //blank
            out.println("<td width='200'>&nbsp;</td>");
            if(!evenTeamIndex) {
              //blank
              out.println("<td width='10'>&nbsp;</td>");
            } else {
              if(playoffRunNumber != numRuns-1) {
                //bridge of size 2^(playoffRunNumber+2) = 1<<(playoffRunNumber+2)
                out.println("<td class='Bridge' rowspan='" + (1<<(playoffRunNumber+2)) + "'>&nbsp;</td>");
              } else {
                out.println("<td rowspan='" + (1<<(playoffRunNumber+2)) + "'>&nbsp;</td>");
              }
            }
          } else if( (row - lastTeam[playoffRunNumber]) == (1 << (playoffRunNumber+2) )/2
                     && 0 != lastTeam[playoffRunNumber] ) {
            //bracket number
            if(!evenTeamIndex) {
              //blank
              out.println("<td width='200'>&nbsp;</td>");
              out.println("<td width='10'>&nbsp;</td>");
            } else {
              if(playoffRunNumber != numRuns-1) {
                out.println("<td width='200'><font size='4'>Bracket " + bracketIndex[playoffRunNumber] + "</font><br /></td>");
                bracketIndex[playoffRunNumber]++;
              } else {
                out.println("<td width='200'>&nbsp;</td>");
              }
              //skip for bridge
              out.println("<!-- skip column for bridge -->");
            }
          } else {
            //blank
            out.println("<td width='200'>&nbsp;</td>");
            if(!evenTeamIndex || 0 == lastTeam[playoffRunNumber]) {
              //blank
              out.println("<td width='10'>&nbsp;</td>");
            } else {
              //skip for bridge
              out.println("<!-- skip column for bridge -->");
            }
          }
        }
        out.println("</tr>");
      }

      out.println("</table");
    } else {
      out.println("<p>Not enough teams for a playoff</p>");
    }
  }
  
  private static void genScoresheetForm(
      final JspWriter out,
      Team teamA,
      Team teamB,
      String tableA,
      String tableB,
      List tables,
      String round) throws IOException
  {
    final String formName = "genScoresheet_" + teamA.getTeamNumber() + "_" + teamB.getTeamNumber();
    out.println("<form name='" + formName + "' action='../scoresheets/generateScoresheet.jsp' method='POST' target='_new'>");
    
    out.println("  <input type='hidden' name='numTeams' value='2'>");

    out.println("  <input type='hidden' name='TeamName1' value='" + teamA.getTeamName() + "'>");
    out.println("  <input type='hidden' name='TeamName2' value='" + teamB.getTeamName() + "'>");

    out.println("  <input type='hidden' name='TeamNumber1' value='" + teamA.getTeamNumber() + "'>");
    out.println("  <input type='hidden' name='TeamNumber2' value='" + teamB.getTeamNumber() + "'>");

    out.println("  1st side:<select name='Table1'>");
    Iterator i = tables.iterator();
    while(i.hasNext())
    {
      final String[] t = (String[])i.next();
      
      out.print("    <option value='" + t[0] + "'");
      if(t[0].equals(tableA)) {
        out.print(" selected");
      }
      out.println(">" + t[0] + "</option>");
      out.print("    <option value='" + t[1] + "'");
      if(t[1].equals(tableA)) {
        out.print(" selected>");
      }
      out.println(">" + t[1] + "</option>");
    }
    out.println("  </select><br/>");
    
    out.println("  2nd side:<select name='Table2'>");
    i = tables.iterator();
    while(i.hasNext())
    {
      final String[] t = (String[])i.next();
      
      out.print("    <option value='" + t[0] + "'");
      if(t[0].equals(tableB)) {
        out.print(" selected");
      }
      out.println(">" + t[0] + "</option>");
      
      out.print("    <option value='" + t[1] + "'");
      if(t[1].equals(tableB)) {
        out.print(" selected");
      }
      out.println(">" + t[1] + "</option>");
    }
    out.println("  </select><br/>");
    
    out.println("  <input type='hidden' name='RoundNumber1' value='" + round + "'>");
    out.println("  <input type='hidden' name='RoundNumber2' value='" + round + "'>");
    
    out.println("  <input type='submit' value='Get Scoresheets'>");
    
    out.println("</form>");
  }
  
  /**
   * Output the table for the printable brackets to out
   */
  public static void displayScoresheetGenerationBrackets(
      final Connection connection, final Document challengeDocument,
      final String division, final JspWriter out) throws IOException,
      SQLException, ParseException {

    final Map tournamentTeams = Queries.getTournamentTeams(connection);
    final String currentTournament = Queries.getCurrentTournament(connection);
    final int numSeedingRounds = Queries.getNumSeedingRounds(connection);
    final List tournamentTables = Queries.getTournamentTables(connection);
    if(tournamentTables.size() == 0)
      tournamentTables.add(new String[] {"",""});

    // initialize currentRound to contain a full bracket setup
    List tempCurrentRound = buildInitialBracketOrder(connection, division,
        tournamentTeams);
    if (tempCurrentRound.size() > 1) {
      out.println("<table align='center' width='100%' border='0' cellpadding='3' cellspacing='0'>");

      // compute number of runs needed to complete the playoffs
      out.println("<tr><th></th>");
      final int initialBracketSize = tempCurrentRound.size();
      int numRuns = 1;
      while (Math.pow(2, numRuns) < initialBracketSize) {
        out.println("<th colspan='2'>Playoff Round " + numRuns + "</th>");
        numRuns++;
      }
      out.println("<th colspan='2'>Playoff Round " + numRuns + "</th>");
      numRuns++;
      out.println("<th colspan='2'>Playoff Round " + numRuns + "</th>");
      out.println("</tr>");

      // the row at which the last team was displayed
      int[] lastTeam = new int[numRuns];
      Arrays.fill(lastTeam, 0);
      // the teams for a given round
      Iterator[] currentRoundTeams = new Iterator[numRuns];
      // the bracket that will be displayed next
      int[] bracketIndex = new int[numRuns];
      Arrays.fill(bracketIndex, 1);
      // the team that will be displayed next
      int[] teamIndex = new int[numRuns];
      Arrays.fill(teamIndex, 1);
      // the table that will be assigned next
      Iterator[] currentRoundTable = new Iterator[numRuns];
      // team iterator for scoresheet generation form
      Iterator[] scoreGenTeams = new Iterator[numRuns];
      // match counts per round
      int[] matchCounts = new int[numRuns];
      Arrays.fill(matchCounts, 0);

      for (int tempRunNumber = 0; tempRunNumber < numRuns; tempRunNumber++) {
        // save off the team order for later
        currentRoundTeams[tempRunNumber] = tempCurrentRound.iterator();
        scoreGenTeams[tempRunNumber] = tempCurrentRound.iterator();
        if(tempRunNumber > 0) {
          matchCounts[tempRunNumber] = matchCounts[tempRunNumber-1];
        }

        final List newCurrentRound = new LinkedList();
        final Iterator prevIter = tempCurrentRound.iterator();
        while (prevIter.hasNext()) {
          final Team teamA = (Team) prevIter.next();
          if (prevIter.hasNext()) {
            final Team teamB = (Team) prevIter.next();
            final Team winner = pickWinner(connection, challengeDocument,
                teamA, teamB, tempRunNumber + numSeedingRounds + 1);
            newCurrentRound.add(winner);
            if( !( (teamA != null && teamA.equals(Team.BYE)) || (teamB != null && teamB.equals(Team.BYE)) ) ) {
              matchCounts[tempRunNumber] += 1;
            }
          } else {
            // handle finals
            newCurrentRound.add(teamA);
          }
        }
        currentRoundTable[tempRunNumber] = tournamentTables.iterator();
        if(tempRunNumber > 0) {
          final int iters = matchCounts[tempRunNumber-1] % tournamentTables.size();
          for(int j=0; j < iters; j++)
          {
            currentRoundTable[tempRunNumber].next();
          }
        }
        tempCurrentRound = newCurrentRound;
      }

      // Now create rows, 4 per initial bracket, two columns for each playoff
      // round
      // row is 1 based
      // playoffRunNumber is 0 based for array indexing
      // (1 << (playoffRunNumber+2)) == number of rows per bracket for the given
      // run
      for (int row = 1; row <= initialBracketSize * 4; row++) {
        out.println("<tr><td width='1' cellpadding='0'>&nbsp;</td>");
        for (int playoffRunNumber = 0; playoffRunNumber < numRuns; playoffRunNumber++) {
          final boolean evenTeamIndex = (teamIndex[playoffRunNumber] % 2 == 0);
          if ((0 == lastTeam[playoffRunNumber] // haven't seen a team yet
              && ((row == ((1 << (playoffRunNumber + 2)) / 2) - 1) // first
                                                                    // team in a
                                                                    // later
                                                                    // bracket
              || (row == 1 && playoffRunNumber == 0))) // first team first
                                                        // round
              || ((row - lastTeam[playoffRunNumber]) == (1 << (playoffRunNumber + 2)))) { // later
                                                                                          // teams

            // keep track of where we last output a team
            lastTeam[playoffRunNumber] = row;
            // team information
            final Team team = (Team) currentRoundTeams[playoffRunNumber].next();
            out.println("<td class='Leaf' width='200'>");
            out.println(getDisplayString(connection, currentTournament,
                (playoffRunNumber + numSeedingRounds + 1), team, false));

            out.println("</td>");

            if (evenTeamIndex) {
              // skip for bridge
              out.println("<!-- skip column for bridge -->");
            } else {
              // bridgetop
              out.println("<td class='BridgeTop' width='10'>&nbsp;</td>");
            }

            teamIndex[playoffRunNumber]++;
          } else if ((row - lastTeam[playoffRunNumber]) == 1
              && 0 != lastTeam[playoffRunNumber]) {
            // bracket number
            if (!evenTeamIndex) {
              out.println("<td width='200'>&nbsp;</td>");
              out.println("<td width='10'>&nbsp;</td>");
            } else {
              if (playoffRunNumber != numRuns - 1) {
                out.println("<td width='200' valign='middle' rowspan='"
                    + ((1 << (playoffRunNumber + 2)) - 1)
                    + "'><font size='4'>Bracket "
                    + bracketIndex[playoffRunNumber] + "</font><br />");
                // TODO: Test that this section isn't broken when only some scores are entered.
                final Team teamA = (Team) scoreGenTeams[playoffRunNumber].next();
                final Team teamB = (Team) scoreGenTeams[playoffRunNumber].next();
                if (teamA != null && teamB != null
                    && !( teamA.equals(Team.BYE) || teamB.equals(Team.BYE) ) ) {
                  if (!currentRoundTable[playoffRunNumber].hasNext()) {
                    currentRoundTable[playoffRunNumber] = tournamentTables.iterator();
                  }
                  final String[] tables = (String[]) currentRoundTable[playoffRunNumber].next();
                  genScoresheetForm(out, teamA, teamB, tables[0], tables[1], tournamentTables,
                      "Playoff Round " + (playoffRunNumber + 1));
                } else if(teamA == null || teamB == null) {
                  if (!currentRoundTable[playoffRunNumber].hasNext()) {
                    currentRoundTable[playoffRunNumber] = tournamentTables.iterator();
                  }
                  currentRoundTable[playoffRunNumber].next();
                }
                out.println("</td>");
                bracketIndex[playoffRunNumber]++;
              } else {
                out.println("<td rowspan='" + (1 << (playoffRunNumber + 2))
                    + "width='200'>&nbsp;</td>");
              }
              // skip for bridge
              out.println("<!-- skip column for bridge -->");

              if (playoffRunNumber != numRuns - 1) {
                // bridge of size 2^(playoffRunNumber+2) =
                // 1<<(playoffRunNumber+2)
                out.println("<td class='Bridge' rowspan='"
                    + (1 << (playoffRunNumber + 2)) + "'>&nbsp;</td>");
              } else {
                out.println("<td rowspan='" + (1 << (playoffRunNumber + 2))
                    + "'>&nbsp;</td>");
              }
            }
          } else {
            if (!evenTeamIndex || 0 == lastTeam[playoffRunNumber]) {
              // blank
              out.println("<td width='200'>&nbsp;</td>");
              out.println("<td width='10'>&nbsp;</td>");
            } else {
              // skip for bridge
              out.println("<!-- skip column for bridge -->");
            }
          }
        }
        out.println("</tr>");
      }
      out.println("</table");
    } else {
      out.println("<p>Not enough teams for a playoff</p>");
    }
  }
  
  /**
   * Array of indicies used to determine who plays who in a single elimination
   * playoff bracket system
   */
  private static final int[][] SEED_ARRAY = new int[][] {
    {1, 2}, // 1 team, just put the team with a bye
    {1, 2}, // 2 teams
    {1, 4, 3, 2}, // 4 teams
    {1, 8, 5, 4, 3, 6, 7, 2}, // 8 teams
    {1, 16, 9, 8, 5, 12, 13, 4, 3, 14, 11, 6, 7, 10, 15, 2}, // 16 teams
    {1, 32, 17, 16, 9, 24, 25, 8, 5, 28, 21, 12, 13, 20, 29, 4, 3, 30, 19, 14, 11, 22, 27, 6, 7, 26, 23, 10, 15, 18, 31, 2}, // 32 teams
    {1, 64, 33, 32, 17, 48, 49, 16, 9, 56, 41, 24, 25, 40, 57, 8, 5, 60, 37, 28, 21, 44, 53, 12, 13, 52, 45, 20, 29, 36, 61, 4, 3, 62, 35, 30, 19, 46, 51, 14, 11, 54, 43, 22, 27, 38, 59, 6, 7, 58, 39, 26, 23, 42, 55, 10, 15, 50, 47, 18, 31, 34, 63, 2}, // 64 teams
    {1, 128, 65, 64, 33, 96, 97, 32, 17, 112, 81, 48, 49, 80, 113, 16, 9, 120, 73, 56, 41, 88, 105, 24, 25, 104, 89, 40, 57, 72, 121, 8, 5, 124, 69, 60, 37, 92, 101, 28, 21, 108, 85, 44, 53, 76, 117, 12, 13, 116, 77, 52, 45, 84, 109, 20, 29, 100, 93, 36, 61, 68, 125, 4, 3, 126, 67, 62, 35, 94, 99, 30, 19, 110, 83, 46, 51, 78, 115, 14, 11, 118, 75, 54, 43, 86, 107, 22, 27, 102, 91, 38, 58, 71, 123, 6, 7, 122, 71, 58, 39, 90, 103, 26, 23, 106, 87, 42, 55, 74, 119, 10, 15, 114, 79, 50, 47, 82, 111, 18, 31, 98, 95, 34, 63, 66, 127, 2} // 128 teams
  };
    
}