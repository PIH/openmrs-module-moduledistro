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
package org.openmrs.module.distribution.api;

import java.io.File;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.ModuleConstants;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;

/**
 * Tests {@link ${DistributionService}}.
 */
public class  DistributionServiceTest extends BaseModuleContextSensitiveTest {
	
	DistributionService service;
	
	@Before
	public void beforeEachTest() throws Exception {
		service = Context.getService(DistributionService.class);
		
		// avoid writing to the actual module folder e.g. ~/.OpenMRS/modules
		File temp = File.createTempFile("findit", "");
		temp.deleteOnExit();
		File tempModuleFolder = new File(temp.getParentFile(), "MODULES" + new Random().nextInt(10000));
		if (!tempModuleFolder.mkdir())
			throw new RuntimeException("Failed to create folder at " + tempModuleFolder.getAbsolutePath());
		GlobalProperty gp = new GlobalProperty(ModuleConstants.REPOSITORY_FOLDER_PROPERTY, tempModuleFolder.getAbsolutePath());
		Context.getAdministrationService().saveGlobalProperty(gp);
	}
	
	@Test
	public void shouldSetupContext() {
		Assert.assertNotNull(service);
	}

	/**
     * @see DistributionService#uploadDistribution(File)
     * @verifies upload omods in a zip
     */
    @Test
    public void uploadDistribution_shouldUploadOmodsInAZip() throws Exception {
    	File distro = new File("src/test/resources/org/openmrs/module/distribution/include/distro.zip");
    	Assert.assertTrue(distro.exists());
    	
	    List<String> log = service.uploadDistribution(distro, null);
	    System.out.println(OpenmrsUtil.join(log, "\n"));
	    Assert.assertTrue(log.contains("Installed uiframework version 1.3"));
	    Assert.assertTrue(log.contains("Installed uilibrary version 1.1"));
	    Assert.assertTrue(log.contains("Started uiframework version 1.3"));
	    Assert.assertTrue(log.contains("Started uilibrary version 1.1"));
    }
    
}
