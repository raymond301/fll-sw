/*
 * Copyright (c) 2011 INSciTE.  All rights reserved
 * INSciTE is on the web at: http://www.hightechkids.org
 * This code is released under GPL; see LICENSE.txt for details.
 */

package fll.web.admin;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import net.mtu.eggplant.util.sql.SQLFunctions;

import org.apache.commons.codec.digest.DigestUtils;

import fll.db.Queries;
import fll.web.ApplicationAttributes;
import fll.web.BaseFLLServlet;
import fll.web.CookieUtils;
import fll.web.DoLogin;
import fll.web.SessionAttributes;

/**
 * Create a user if.
 */
@WebServlet("/admin/CreateUser")
public class CreateUser extends BaseFLLServlet {

  @Override
  protected void processRequest(final HttpServletRequest request,
                                final HttpServletResponse response,
                                final ServletContext application,
                                final HttpSession session) throws IOException, ServletException {

    PreparedStatement prep = null;
    final DataSource datasource = ApplicationAttributes.getDataSource(application);
    Connection connection = null;
    try {
      connection = datasource.getConnection();

      final String user = request.getParameter("user");
      final String pass = request.getParameter("pass");
      final String passCheck = request.getParameter("pass_check");
      if (null == pass
          || null == passCheck || null == user || user.isEmpty() || pass.isEmpty() || passCheck.isEmpty()) {
        session.setAttribute(SessionAttributes.MESSAGE,
                             "<p class='error'>You must enter all information in the form.</p>");
        response.sendRedirect(response.encodeRedirectURL("createUsername.jsp"));
        return;
      }

      if (!pass.equals(passCheck)) {
        session.setAttribute(SessionAttributes.MESSAGE,
                             "<p class='error'>Password and password check do not match.</p>");
        response.sendRedirect(response.encodeRedirectURL("createUsername.jsp"));
        return;
      }

      final String hashedPass = DigestUtils.md5Hex(pass);
      prep = connection.prepareStatement("INSERT INTO fll_authentication (fll_user, fll_pass) VALUES(?, ?)");
      prep.setString(1, user);
      prep.setString(2, hashedPass);
      prep.executeUpdate();

      session.setAttribute(SessionAttributes.MESSAGE, "<p id='success-create-user'>Successfully created user '"
          + user + "'</p>");

      // do a login if not already logged in
      final Collection<String> loginKeys = CookieUtils.findLoginKey(request);
      final String authenticatedUser = Queries.checkValidLogin(connection, loginKeys);
      if (null == authenticatedUser) {
        DoLogin.doLogin(request, response, application, session);
      } else {
        response.sendRedirect(response.encodeRedirectURL("index.jsp"));
      }

    } catch (final SQLException e) {
      throw new RuntimeException(e);
    } finally {
      SQLFunctions.close(prep);
      SQLFunctions.close(connection);
    }

  }
}
