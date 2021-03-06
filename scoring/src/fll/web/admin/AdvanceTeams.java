/*
 * Copyright (c) 2000-2002 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */
package fll.web.admin;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import net.mtu.eggplant.util.sql.SQLFunctions;

import org.apache.log4j.Logger;

import fll.Team;
import fll.db.Queries;
import fll.util.LogUtils;
import fll.web.ApplicationAttributes;
import fll.web.BaseFLLServlet;
import fll.web.SessionAttributes;

/**
 * Advance teams to the next tournament.
 */
@WebServlet("/admin/AdvanceTeams")
public class AdvanceTeams extends BaseFLLServlet {

  private static final Logger LOGGER = LogUtils.getLogger();

  protected void processRequest(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final ServletContext application,
                                final HttpSession session) throws IOException, ServletException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Top of AdvanceTeams.doPost");
    }

    final StringBuilder message = new StringBuilder();
    final DataSource datasource = ApplicationAttributes.getDataSource(application);

    // can't put types inside a session
    @SuppressWarnings("unchecked")
    final List<Team> teamsToAdvance = SessionAttributes.getNonNullAttribute(session, "advancingTeams", List.class);

    Connection connection = null;
    try {
      connection = datasource.getConnection();

      for (final Team team : teamsToAdvance) {
        Queries.advanceTeam(connection, team.getTeamNumber());
      }
      message.append("<p><i>Successfully advanced teams</i></p>");
    } catch (final SQLException e) {
      LOGGER.error("There was an error talking to the database", e);
      throw new RuntimeException("There was an error talking to the database", e);
    } finally {
      SQLFunctions.close(connection);
    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Bottom of AdvanceTeams.doPost");
    }

    if (message.length() > 0) {
      session.setAttribute("message", message.toString());
    }

    response.sendRedirect(response.encodeRedirectURL("index.jsp"));
  }

}
