<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<%@ include file="template/localHeader.jsp"%>

<h1><spring:message code="distribution.manage"/></h1>

<fieldset>
	<legend>Upload a Distribution</legend>
	<form method="post" enctype="multipart/form-data" action="manage-upload.form">
		Distribution ZIP file:
		<input type="file" name="distributionZip"/>
		<br/>
		<input type="submit" value="<spring:message code="general.upload"/>"/>
	</form>
</fieldset>

<%@ include file="/WEB-INF/template/footer.jsp"%>