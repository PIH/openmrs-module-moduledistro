package org.openmrs.module.moduledistro.api.impl;


import java.io.File;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.moduledistro.api.impl.ModuleDistroServiceImpl.UploadedModule;

public class ModuleDistroServiceImplTest {
	
	ModuleDistroServiceImpl serviceImpl;
	
	@Before
	public void beforeEachTest() {
		serviceImpl = new ModuleDistroServiceImpl();
	}
	
	/**
	 * @see ModuleDistroServiceImpl#populateFields(UploadedModule)
	 * @verifies read the id and version from config.xml
	 */
	@Test
	public void populateIdAndVersion_shouldReadTheIdAndVersionFromConfigxml() throws Exception {
		File module = new File("src/test/resources/org/openmrs/module/moduledistro/include/appframework-1.0.omod");
		Assert.assertTrue(module.exists()); 
		UploadedModule info = serviceImpl.new UploadedModule(null, module);
		serviceImpl.populateFields(info);
		Assert.assertEquals("appframework", info.getModuleId());
		Assert.assertEquals("1.0", info.getModuleVersion());
	}

	/**
     * @see ModuleDistroServiceImpl#shouldInstallNewVersion(String,String)
     * @verifies return false for 1.0-SNAPSHOT versus 1.0
     */
    @Test
    public void shouldInstallNewVersion_shouldReturnFalseFor10SNAPSHOTVersus10() throws Exception {
	    Assert.assertFalse(serviceImpl.shouldInstallNewVersion("1.0-SNAPSHOT", "1.0"));
    }

	/**
     * @see ModuleDistroServiceImpl#shouldInstallNewVersion(String,String)
     * @verifies return true for 1.0 versus 1.0-SNAPSHOT
     */
    @Test
    public void shouldInstallNewVersion_shouldReturnTrueFor10Versus10SNAPSHOT() throws Exception {
    	Assert.assertTrue(serviceImpl.shouldInstallNewVersion("1.0", "1.0-SNAPSHOT"));
    }

	/**
     * @see ModuleDistroServiceImpl#shouldInstallNewVersion(String,String)
     * @verifies return true for 1.0-SNAPSHOT versus 1.0-SNAPSHOT
     */
    @Test
    public void shouldInstallNewVersion_shouldReturnTrueFor10SNAPSHOTVersus10SNAPSHOT() throws Exception {
    	Assert.assertTrue(serviceImpl.shouldInstallNewVersion("1.0-SNAPSHOT", "1.0-SNAPSHOT"));
    }

	/**
     * @see ModuleDistroServiceImpl#shouldInstallNewVersion(String,String)
     * @verifies return true for 1.0 versus 0.9.5
     */
    @Test
    public void shouldInstallNewVersion_shouldReturnTrueFor10Versus095() throws Exception {
    	Assert.assertTrue(serviceImpl.shouldInstallNewVersion("1.0", "0.9.5"));
    }

	/**
     * @see ModuleDistroServiceImpl#shouldInstallNewVersion(String,String)
     * @verifies return false for 0.9.5 versus 1.0
     */
    @Test
    public void shouldInstallNewVersion_shouldReturnFalseFor095Versus10() throws Exception {
    	Assert.assertFalse(serviceImpl.shouldInstallNewVersion("0.9.5", "1.0"));
    }

	/**
     * @see ModuleDistroServiceImpl#shouldInstallNewVersion(String,String)
     * @verifies return true for 1.0-SNAPSHOT versus 0.9
     */
    @Test
    public void shouldInstallNewVersion_shouldReturnTrueFor10SNAPSHOTVersus09() throws Exception {
    	Assert.assertTrue(serviceImpl.shouldInstallNewVersion("1.0-SNAPSHOT", "0.9"));
    }

	/**
     * @see ModuleDistroServiceImpl#shouldInstallNewVersion(String,String)
     * @verifies return false for 1.0-SNAPSHOT versus 1.1
     */
    @Test
    public void shouldInstallNewVersion_shouldReturnFalseFor10SNAPSHOTVersus11() throws Exception {
    	Assert.assertFalse(serviceImpl.shouldInstallNewVersion("1.0-SNAPSHOT", "1.1"));
    }
}