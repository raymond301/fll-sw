<%@ include file="/WEB-INF/jspf/init.jspf" %>
      
<html>
  <head>
    <link rel="stylesheet" type="text/css" href="<c:url value='/style/style.jsp'/>" />
    <title><x:out select="$challengeDocument/fll/@title"/> (Credits)</title>
  </head>

  <body>
    <h1><x:out select="$challengeDocument/fll/@title"/> (Credits)</h1>

    <p>This software is licensed by INSciTE under the <a
    href="LICENSE.txt">GPL</a></p>

    <p>Developers:</p>
    <ul>
      <li><a href="http://mtu.net/~jpschewe">Jon Schewe</a></li>
      <li><a href="http://mtu.net/~engstrom">Eric Engstrom</a></li>
      <li>Dan Churchill</li>
    </ul>

    <p>Testers (besides the developers):</p>
    <ul>
      <li>Chuck Davis</li>
    </ul>
      
    <p>The following software packages are used in the application:</p>
    <ul>
      <li><a href="http://mtu.net/~jpschewe/JonsInfra/index.html">JonsInfra</a> -
        <a href="JonsInfra-license.txt">License</a></li>
      
      <li><a href="http://www.servletsuite.com/servlets/uptag.htm">Upload taglib</a> -
        <a href="upload-taglib.txt">License</a></li>

      <li><a href="http://jakarta.apache.org/tomcat/index.html">Apache Tomcat</a> -
        <a href="tomcat-license.txt">License</a></li>

      <li><a href="http://java.sun.com/">Java Development Kit</a> -
        <a href="jdk-license.txt">License</a></li>

      <li><a href="http://jakarta.apache.org/commons/lang/">Jakarta Commons Lang</a> -
        <a href="commons-lang-LICENSE.txt">License</a></li>

      <li><a href="http://jakarta.apache.org/taglibs/doc/string-doc/string-1.0.1/index.html">String Tag library</a> -
        <a href="taglib-string-LICENSE.txt">License</a></li>
        
      <li><a href="http://www.lowagie.com/iText/">iText (PDF generation library)</a> -
        <a href="iText-license.txt">License Information</a></li>
        
    </ul>

<%@ include file="/WEB-INF/jspf/footer.jspf" %>
  </body>
</html>