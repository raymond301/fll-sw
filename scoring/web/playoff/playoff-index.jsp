<%@ include file="/WEB-INF/jspf/init.jspf"%>

<c:if test="${not servletLoaded }">
 <c:redirect url="index.jsp" />
</c:if>
<c:remove var="servletLoaded" />

<html>
<head>
<link rel="stylesheet" type="text/css"
 href="<c:url value='/style/style.jsp'/>" />
<title>Playoff's</title>
</head>

<body>
 <h1>Playoff menu</h1>

 ${message}
 <%-- clear out the message, so that we don't see it again --%>
 <c:remove var="message" />

 <ol>
  <li>If using the automatic table assignment feature for
   scoresheet generation, make certain to set up labels for each of your
   tables, available from the Admin page or by clicking <a
   href='<c:url value="/admin/tables.jsp"/>'>here</a>.
  </li>

  <li>Check to make sure all teams have scores entered for each
   seeding round.<br />
   <form name='check' action='CheckSeedingRounds' method='POST'>
    Select Division: <select id='check-division' name='division'>
     <c:forEach items="${playoff_data.eventDivisions }" var="division">
      <option value='${division}'>${division}</option>
     </c:forEach>
    </select> <input type='submit' id='check_seeding_rounds'
     value='Check Seeding Rounds' />
   </form>
  </li>


  <li><b>WARNING: Do not initialize playoff brackets for a
    division until all seeding runs for that division have been
    recorded!</b> Doing so will automatically add bye runs to the teams that
   don't have enough seeding runs. If creating a new playoff division it
   is assumed that you know how many runs each team has completed.<br />
   <form name='initialize' action='InitializeBrackets' method='POST'>
    Select Division: <select id='initialize-division' name='division'>
     <c:forEach items="${playoff_data.initDivisions }" var="division">
      <option value='${division}'>${division}</option>
     </c:forEach>
    </select><br> <input type='checkbox' name='enableThird' value='yes' />Check
    to enable 3rd/4th place brackets<br> <input type='submit'
     id='initialize_brackets' value='Initialize Brackets' />
   </form></li>


  <c:if test="${not empty playoff_data.existingDivisions }">

   <li>
    <form name='admin' action='adminbrackets.jsp' method='get'>
     <b>Printable Brackets</b><br /> Select Division: <select
      name='division'>
      <c:forEach items="${playoff_data.existingDivisions }"
       var="division">
       <option value='${division}'>${division}</option>
      </c:forEach>
     </select> from round <select name='firstRound'>
      <c:forEach begin="1" end="${playoff_data.numPlayoffRounds }"
       var="numRounds">
       <c:choose>
        <c:when test="${numRounds == 1 }">
         <option value='${numRounds }' selected>${numRounds }</option>
        </c:when>
        <c:otherwise>
         <option value='${numRounds }'>${numRounds }</option>
        </c:otherwise>
       </c:choose>
      </c:forEach>
     </select> to
     <%-- numPlayoffRounds+1 == the column in which the 1st place winner is displayed  --%>
     <select name='lastRound'>
      <c:forEach begin="2" end="${playoff_data.numPlayoffRounds+1 }"
       var="numRounds">
       <c:choose>
        <c:when test="${numRounds == numPlayoffRounds+1 }">
         <option value='${numRounds }' selected>${numRounds }</option>
        </c:when>
        <c:otherwise>
         <option value='${numRounds }'>${numRounds }</option>
        </c:otherwise>
       </c:choose>
      </c:forEach>
     </select> <input type='submit' id='display_printable_brackets'
      value='Display Brackets'>
    </form>
   </li>


   <%-- scoresheet generation --%>
   <li>
    <form name='printable' action='scoregenbrackets.jsp' method='get'>
     <b>Scoresheet Generation Brackets</b><br />

     <%-- division --%>
     Select Division: <select name='division'>
      <c:forEach items="${playoff_data.existingDivisions }"
       var="division">
       <option value='${division}'>${division}</option>
      </c:forEach>
     </select>

     <%-- select rounds --%>
     from round <select name='firstRound'>
      <c:forEach begin="1" end="${playoff_data.numPlayoffRounds }"
       var="numRounds">
       <c:choose>
        <c:when test="${numRounds == 1 }">
         <option value='${numRounds }' selected>${numRounds }</option>
        </c:when>
        <c:otherwise>
         <option value='${numRounds }'>${numRounds }</option>
        </c:otherwise>
       </c:choose>
      </c:forEach>
     </select> to

     <%-- numPlayoffRounds+1 == the column in which the 1st place winner is displayed  --%>
     <select name='lastRound'>
      <c:forEach begin="2" end="${playoff_data.numPlayoffRounds+1 }"
       var="numRounds">
       <c:choose>
        <c:when test="${numRounds == playoff_data.numPlayoffRounds+1 }">
         <option value='${numRounds }' selected>${numRounds }</option>
        </c:when>
        <c:otherwise>
         <option value='${numRounds }'>${numRounds }</option>
        </c:otherwise>
       </c:choose>
      </c:forEach>
     </select>

     <%-- submit--%>
     <input type='submit' id='display_scoregen_brackets'
      value='Display Brackets'>
    </form>
   </li>
   <%-- end scoresheet generation --%>

   <li><b>Scrolling Brackets</b> (as on big screen display)<br />
    <a href="remoteMain.jsp">Display brackets</a><br /> Division and
    round must be selected from the big screen display <a
    href="<c:url value='/admin/remoteControl.jsp'/>">remote control</a>
    page.</li>

  </c:if>
  <!-- if playoff divisions not empty -->
 </ol>


</body>
</html>
