<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core" %>

<c:set var="${response.contentType}" value="text/css" />

BODY { 
  background: url(<c:url value="/images/bricks1.gif"/>) white;
  margin-top: 4;
}

.help {
  background-color:#fafafa;
  border:1px solid black;
  width:80%;
  padding: 3px;
  font-family:sans-serif;
  font-size: 10pt;
}
