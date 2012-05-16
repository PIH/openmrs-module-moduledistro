<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<%@ include file="template/localHeader.jsp"%>

<fieldset>
	<legend><spring:message code="moduledistro.upload.heading"/></legend>
	<form method="post" enctype="multipart/form-data" action="manage-upload.form">
		<spring:message code="moduledistro.upload.zip"/>:
		<input type="file" name="distributionZip"/>
		<br/>
		<input type="submit" value="<spring:message code="moduledistro.upload.submit"/>"/>
	</form>
</fieldset>

<%@ include file="/WEB-INF/template/footer.jsp"%>