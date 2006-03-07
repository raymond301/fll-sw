<%@ include file="/WEB-INF/jspf/init.jspf" %>

<%@ page import="fll.Utilities" %>
<%@ page import="fll.Queries" %>

<%@ page import="java.sql.Statement" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.sql.Connection" %>
  
<%
final Connection connection = (Connection)application.getAttribute("connection");
final Statement stmt = connection.createStatement();
final ResultSet rs = stmt.executeQuery("SELECT MAX(RunNumber) FROM Performance WHERE Tournament = '" + Queries.getCurrentTournament(connection) + "'");
final int maxRunNumber;
if(rs.next()) {
  maxRunNumber = rs.getInt(1);
} else {
  maxRunNumber = 1;
}
Utilities.closeResultSet(rs);
Utilities.closeStatement(stmt);
%>
<html>
  <head>
    <link rel="stylesheet" type="text/css" href="<c:url value='/style/style.jsp'/>" />
    <title><x:out select="$challengeDocument/fll/@title"/> (Reporting)</title>
  </head>

  <body>
    <h1><x:out select="$challengeDocument/fll/@title"/> (Reporting)</h1>

    <ol>
      <li><a href="summarizePhase1.jsp">Compute summarized scores</a>.  This
      needs to be executed before any reports can be generated.  You will be
      returned to this page if there are no errors summarizing scores.</li>

      <li><a href="finalComputedScores.jsp">Final Computed Scores</a> <a href="finalComputedScores_pdf.jsp" target="_new">PDF version</a></li>
        
      <li><a href="categorizedScores.jsp">Categorized Scores</a></li>

      <li><a href="scoreGroupScores.jsp">Categorized Scores by score group</a></li>

    </ol>

    <p>Some reports that are handy for intermediate reporting and
    checking of the current tournament state.</p>
      
    <ul>
      <li>
        <form ACTION='performanceRunReport.jsp' METHOD='POST'>
        Show scores for performance run <select name='RunNumber'>
<% for(int i=0; i<maxRunNumber; i++) { %>
  <option value='<%=(i+1)%>'><%=(i+1)%></option>
<% } %>
        </select>
        <input type='submit' value='Show Scores'>
        </form>
      </li>

      <li>
        <form action='teamPerformanceReport.jsp' method='post'>
          Show performance scores for team <select name='TeamNumber'>
            <% pageContext.setAttribute("tournamentTeams", Queries.getTournamentTeams(connection).values()); %>
            <c:forEach items="${tournamentTeams}" var="team">
              <option value='<c:out value="${team.teamNumber}"/>'><c:out value="${team.teamNumber}"/> - <c:out value="${team.teamName}"/></option>
            </c:forEach>
          </select>
          <input type='submit' value='Show Scores'/>
        </form>
      </li>
        
    </ul>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>
  </body>
</html>