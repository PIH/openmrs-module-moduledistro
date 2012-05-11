<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<%@ include file="template/localHeader.jsp"%>

<h1><spring:message code="distribution.manage"/></h1>

<pre><c:forEach var="item" items="${ log }">${ item }
</c:forEach>
</pre>

<%@ include file="/WEB-INF/template/footer.jsp"%>