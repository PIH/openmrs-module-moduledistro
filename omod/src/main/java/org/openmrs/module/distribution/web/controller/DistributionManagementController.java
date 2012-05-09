/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.distribution.web.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.distribution.api.DistributionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * The main controller.
 */
@Controller
public class  DistributionManagementController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	@RequestMapping(value = "/module/distribution/manage", method = RequestMethod.GET)
	public void manage(ModelMap model) {
		model.addAttribute("user", Context.getAuthenticatedUser());
	}
	
	@RequestMapping(value = "/module/distribution/manage-upload", method = RequestMethod.POST)
	public void handleUpload(@RequestParam("distributionZip") MultipartFile uploaded) {
		// write this to a known file on disk, so we can use ZipFile, since ZipInputStream is buggy
		File file = null;
		try {
			file = File.createTempFile("distribution", ".zip");
			FileUtils.copyInputStreamToFile(uploaded.getInputStream(), file);
		}
		catch (Exception ex) {
			throw new RuntimeException("Error getting uploaded data", ex);
		}
		
		List<String> log = Context.getService(DistributionService.class).uploadDistribution(file);
		
	}

}
