<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core" %>

<%@ include file="/WEB-INF/jspf/initializeApplicationVars.jspf" %>

<%@ page import="fll.Utilities" %>
  
<%@ page import="org.w3c.dom.Document" %>

<%@ page import="java.sql.Statement" %>
<%@ page import="java.sql.Connection" %>
<%@ page import="java.sql.ResultSet" %>
  
<%
final Document challengeDocument = (Document)application.getAttribute("challengeDocument");
final Connection connection = (Connection)application.getAttribute("connection");
%>

<html>
  <head>
    <link rel="stylesheet" type="text/css" href="<c:url value='/style/style.jsp'/>" />
  <title><%=challengeDocument.getDocumentElement().getAttribute("title")%> (Select Team)</title>

  <!--<style type='text/css'>
   SELECT {line-height: 150%; font-size: 10pt; font-weight: bold; background-color: black }
   OPTION {color: #e0e0e0;}
   INPUT  {font-size: 10pt; font-weight: bold; background-color: black;color:#e0e0e0 }
  </style>-->
  <style type='text/css'>
   SELECT {line-height:150%; font-size:10pt; font-weight:bold; background:black; color:#e0e0e0; }
   OPTION {color:#e0e0e0; }
   INPUT  {font-size:10pt; font-weight:bold; background-color:black; color:#e0e0e0; }
  </style>

  </head>
  <body>
      
    <form action="editTeam.jsp" method="POST" name="selectTeam">
      <!-- top info bar -->
      <table width="100%" border="0" cellpadding="0" cellspacing="0">
      <tr>
        <td align="center" valign="middle" bgcolor="#e0e0e0" colspan='3'>
          <table border="1" cellpadding="0" cellspacing="0" width="100%">
          <tr align="center" valign="middle"><td>
      
          <table border="0" cellpadding="5" cellspacing="0" width="90%">
            <tr>
              <td valign="middle" align="center">
                <font face="Arial" size="4"><%=challengeDocument.getDocumentElement().getAttribute("title")%></font>
              </td>
            </tr>
            <tr align="center">
              <td>
                <font face="Arial" size="4">Please Select Team:</font>
              </td>
            </tr>
          </table>
      
            </td></tr>
            </table>
        </td>
      </tr>
      </table>
    
        <table>
          
          <tr align='left' valign='top'>
          
            <!-- pick team from a list -->            
            <td>
              <br><br>
              <font face='arial' size='4'>Select to Edit Team From List:</font><br>
              <select size='20' name='teamNumber'>
                <%
                final Statement stmt = connection.createStatement();
                final ResultSet rs = stmt.executeQuery("SELECT TeamNumber,TeamName,Organization FROM Teams ORDER BY TeamNumber ASC");
                while(rs.next()) {
                  final int teamNumber = rs.getInt(1);
                  final String teamName = rs.getString(2);
                  final String organization = rs.getString(3);
                  out.print("<option value=");
                  out.print(String.valueOf(teamNumber));
                  out.print(">");
                  out.print(String.valueOf(teamNumber));
                  out.print(" &nbsp;&nbsp;&nbsp;[");
                  out.print(teamName);
                  out.print("] ");
                  out.print(organization);
                  out.print("</option>\n");
                }
                Utilities.closeResultSet(rs);
                Utilities.closeStatement(stmt);
                %>
              </select>
            </td>
            </tr>

          <tr>
            <td align='center'>
              <input type="submit" value="Submit">
            </td>
          </tr>
          
        </table>
      </form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
  </body>
</html>
