/*
 * Copyright (c) 2012 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.web.admin;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import net.mtu.eggplant.xml.NodelistElementCollectionAdapter;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fll.JudgeInformation;
import fll.db.Queries;
import fll.util.LogUtils;
import fll.web.ApplicationAttributes;
import fll.web.BaseFLLServlet;
import fll.web.SessionAttributes;

/**
 * Verify the judges information
 */
@WebServlet("/admin/VerifyJudges")
public class VerifyJudges extends BaseFLLServlet {

  private static final Logger LOGGER = LogUtils.getLogger();

  @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "XSS_REQUEST_PARAMETER_TO_JSP_WRITER", justification = "Checking category name retrieved from request against valid category names")
  protected void processRequest(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final ServletContext application,
                                final HttpSession session) throws IOException, ServletException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Top of VerifyJudges.processRequest");
    }

    try {
      final DataSource datasource = SessionAttributes.getDataSource(session);
      final Connection connection = datasource.getConnection();

      final int tournament = Queries.getCurrentTournament(connection);

      final Document challengeDocument = ApplicationAttributes.getChallengeDocument(application);

      // keep track of any errors
      final StringBuilder error = new StringBuilder();

      // populate a hash where key is category name and value is an empty
      // Set. Use set so there are no duplicates
      final List<Element> subjectiveCategories = new NodelistElementCollectionAdapter(
                                                                                      challengeDocument.getDocumentElement()
                                                                                                       .getElementsByTagName("subjectiveCategory")).asList();

      //FIXME put hidden field in for num rows in judges.jsp
      
      final Collection<JudgeInformation> judges = new LinkedList<JudgeInformation>();

      // walk request and push judge id into the Set, if not null or empty,
      // in the value for each category in the hash.
      int row = 1;
      String id = request.getParameter("id"
          + row);
      String category = request.getParameter("cat"
          + row);
      String station = request.getParameter("station"
          + row);
      while (null != category) {
        if (null != id) {
          id = id.trim();
          id = id.toUpperCase();
          if (id.length() > 0) {
            final JudgeInformation judge = new JudgeInformation(id, category, station);
            judges.add(judge);
          }
        }

        row++;
        id = request.getParameter("id"
            + row);
        category = request.getParameter("cat"
            + row);
        station = request.getParameter("station"
            + row);
      }

      // check that each judging station has a judge for each category
      final Collection<String> judgingStations = Queries.getJudgingStations(connection, tournament);
      for (final String jstation : judgingStations) {
        for (final Element element : subjectiveCategories) {
          final String categoryName = element.getAttribute("name");
          boolean found = false;

          for (final JudgeInformation judge : judges) {
            if (judge.getCategory().equals(categoryName)
                && judge.getStation().equals(jstation)) {
              found = true;
            }
          }

          if (!found) {
            error.append("You must specify at least one judge for category '"
                + categoryName + "' at judging station '" + jstation + "'<br/>");
          }

        }

      }

      session.setAttribute(GatherJudgeInformation.JUDGES_KEY, judges);

      if (error.length() > 0) {
        session.setAttribute(SessionAttributes.MESSAGE, "<p class='error' id='error'>"
            + error.toString() + "</p>");
        response.sendRedirect(response.encodeRedirectURL("judges.jsp"));
      } else {
        response.sendRedirect(response.encodeRedirectURL("displayJudges.jsp"));
      }

    } catch (final SQLException e) {
      LOGGER.error("There was an error talking to the database", e);
      throw new RuntimeException("There was an error talking to the database", e);
    }

  }

}
