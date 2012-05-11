package org.openmrs.module.distribution.api.impl;


import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.openmrs.module.distribution.api.impl.DistributionServiceImpl.UploadedModule;

public class DistributionServiceImplTest {
	
	/**
	 * @see DistributionServiceImpl#populateFields(UploadedModule)
	 * @verifies read the id and version from config.xml
	 */
	@Test
	public void populateIdAndVersion_shouldReadTheIdAndVersionFromConfigxml() throws Exception {
		File module = new File("src/test/resources/org/openmrs/module/distribution/include/appframework-1.0.omod");
		Assert.assertTrue(module.exists());
		DistributionServiceImpl dsi = new DistributionServiceImpl(); 
		UploadedModule info = dsi.new UploadedModule(null, module);
		dsi.populateFields(info);
		Assert.assertEquals("appframework", info.getModuleId());
		Assert.assertEquals("1.0", info.getModuleVersion());
	}
}