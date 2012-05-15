package org.openmrs.module.moduledistro.api.impl;


import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.openmrs.module.moduledistro.api.impl.ModuleDistroServiceImpl;
import org.openmrs.module.moduledistro.api.impl.ModuleDistroServiceImpl.UploadedModule;

public class ModuleDistroServiceImplTest {
	
	/**
	 * @see ModuleDistroServiceImpl#populateFields(UploadedModule)
	 * @verifies read the id and version from config.xml
	 */
	@Test
	public void populateIdAndVersion_shouldReadTheIdAndVersionFromConfigxml() throws Exception {
		File module = new File("src/test/resources/org/openmrs/module/moduledistro/include/appframework-1.0.omod");
		Assert.assertTrue(module.exists());
		ModuleDistroServiceImpl dsi = new ModuleDistroServiceImpl(); 
		UploadedModule info = dsi.new UploadedModule(null, module);
		dsi.populateFields(info);
		Assert.assertEquals("appframework", info.getModuleId());
		Assert.assertEquals("1.0", info.getModuleVersion());
	}
}