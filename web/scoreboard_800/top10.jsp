<%@ include file="/WEB-INF/jspf/init.jspf" %>

<%@ page import="fll.Utilities" %>
<%@ page import="fll.Queries" %>
  
<%@ page import="java.sql.Connection" %>
<%@ page import="java.sql.Statement" %>
<%@ page import="java.sql.ResultSet" %>
  
<%@ page import="java.util.List" %>
  
<%
final Connection connection = (Connection)application.getAttribute("connection");
final String currentTournament = Queries.getCurrentTournament(connection);

Integer divisionIndexObj = (Integer)session.getAttribute("divisionIndex");
int divisionIndex;
if(null == divisionIndexObj) {
  divisionIndex = 0;
} else {
  divisionIndex = divisionIndexObj.intValue();
}
divisionIndex++;
final List divisions = Queries.getDivisions(connection);
if(divisionIndex >= divisions.size()) {
  divisionIndex = 0;
}
pageContext.setAttribute("headerColor", Queries.getColorForDivisionIndex(divisionIndex));
session.setAttribute("divisionIndex", new Integer(divisionIndex));
pageContext.setAttribute("division", divisions.get(divisionIndex));
%>

<HTML>
<head>
<style>
        FONT {color: #ffffff; font-family: "Arial"}
</style>
  <script language=javascript>
    window.setInterval("location.href='top10.jsp'",30000);
  </script>
</head>

<body bgcolor='#000080'>
<center>
<table border='0' cellpadding='0' cellspacing='0' width='98%'>
<tr align='center'>
        <td colspan='5' bgcolor='<c:out value="${headerColor}"/>'>
          <font size='3'>
            <b>Top Ten Performance Scores: Division <c:out value="${division}"/></b>
          </font>
        </td>
</tr>
<!-- <tr>
  <td colspan='5'><img src='<c:url value="/images/blank.gif"/>' width='1' height='4'></td>
</tr> -->
<tr align='center' valign='middle'>
  <td width='7%'><font size='2'><b>Rank</b></font></td>
  <td width='10%'><font size='2'><b>Team&nbsp;Num.</b></font></td>
  <td width='28%'><font size='2'><b>Team&nbsp;Name</b></font></td>
  <!-- <td><font size='2'><b><br>Organization</b></font></td> -->
  <td width='8%'><font size='2'><b>Score</b></font></td>
</tr>
<tr>
  <td colspan='5' align='center'>
<!-- scores here -->
        <table border='1' bordercolor='#aaaaaa' cellpadding='4' cellspacing='0' width='100%'>
        
<%
                    // See top10 for regular scoreboard
  
                final Statement stmt = connection.createStatement();
                final String sql = "SELECT Teams.TeamName, Teams.Organization, Performance.TeamNumber, MAX(Performance.ComputedTotal) AS MaxOfComputedScore"
                  + " FROM Teams,Performance"
                  + " WHERE Performance.Tournament = '" + currentTournament + "'"
                  + " AND Teams.TeamNumber = Performance.TeamNumber"
                  + " AND Performance.RunNumber <= " + Queries.getNumSeedingRounds(connection)
                  + " AND Performance.NoShow = 0"
                  + " AND Performance.Bye = 0"
                  + " AND Teams.Division = '" + pageContext.getAttribute("division") + "'"
                  + " GROUP BY Performance.TeamNumber"
                  + " ORDER BY MaxOfComputedScore DESC LIMIT 10";
                final ResultSet rs = stmt.executeQuery(sql);

                int prevScore = -1;
                int i = 1;
                int rank = 0;
                while(rs.next() &&  i <= 10) {
                  final int score = rs.getInt("MaxOfComputedScore");
                  if(score != prevScore) {
                    rank = i;
                  }
%>      
          <tr align='left'>
            <td width='7%' align='right'>
              <font size='3'>
              <%=rank%>
              </font>
            </td>
            <td width='10%' align='right'>
              <font size='3'>
              <%=rs.getInt("TeamNumber")%>
              </font>
            </td>
            <td width='28%'>
              <font size='3'>
	      <%
              String teamName = rs.getString("TeamName");
              if(null != teamName && teamName.length() > 18) {
                teamName = teamName.substring(0, 18);
              }
              out.println(teamName + "&nbsp;");
              %>
              </font>
            </td>
<!--            <td>
              <font size='3'>
	      <%
              String organization = rs.getString("Organization");
              if(null != organization && organization.length() > 32) {
                organization = organization.substring(0, 32);
              }
              out.println(organization + "&nbsp;");
              %>
              </font>
            </td> -->
            <td width='8%' align='right'>
              <font size='3'>
              <% out.println(score); %>
              </font>
            </td>
          </tr>
<%
                        prevScore = score;
                        i=i+1;
                }//end while
%>
        </table>
<!-- end scores -->
  </td>
</tr>
</table>
</center>
</body>
<%
  Utilities.closeResultSet(rs);
  Utilities.closeStatement(stmt);
%>

</HTML>
