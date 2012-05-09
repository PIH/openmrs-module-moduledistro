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
package org.openmrs.module.distribution.api.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.ModuleUtil;
import org.openmrs.module.distribution.api.DistributionService;
import org.openmrs.module.distribution.api.db.DistributionDAO;

/**
 * It is a default implementation of {@link DistributionService}.
 */
public class DistributionServiceImpl extends BaseOpenmrsService implements DistributionService {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	private DistributionDAO dao;
	
	/**
     * @param dao the dao to set
     */
    public void setDao(DistributionDAO dao) {
	    this.dao = dao;
    }

    /**
     * @see org.openmrs.module.distribution.api.DistributionService#uploadDistribution(java.io.File)
     */
    @Override
    public List<String> uploadDistribution(File distributionZip) {
    	// get all omods included in the zip file, by their original filename
    	List<ModuleInfo> includedOmods = new ArrayList<DistributionServiceImpl.ModuleInfo>();
		
		ZipFile zf = null;
		try {
			zf = new ZipFile(distributionZip);
			for (@SuppressWarnings("rawtypes") Enumeration e = zf.entries(); e.hasMoreElements(); ) {
				ZipEntry entry = (ZipEntry) e.nextElement();
				if (!entry.getName().endsWith(".omod")) {
					throw new RuntimeException("This ZIP is only allowed to contain omod files, but this contains: " + entry.getName());
				}
				File file = File.createTempFile("distributionOmod", ".omod");
				FileUtils.copyInputStreamToFile(zf.getInputStream(entry), file);
				String originalName = simpleFilename(entry.getName());
				includedOmods.add(new ModuleInfo(originalName, file));
			}
		}
        catch (IOException ex) {
	        // TODO something prettier
        	throw new RuntimeException("Error reading zip file", ex);
        }
		finally {
			try {
				zf.close();
			} catch (Exception ex) { }
		}

		// determine which omods we want to install
		for (ModuleInfo candidate : includedOmods) {
			try {
	            populateIdAndVersion(candidate);
            }
            catch (IOException ex) {
	            throw new RuntimeException("Error inspecting " + candidate.getOriginalFilename(), ex);
            }
			populateSkipReason(candidate);
		}
		
		// install those omods
		File modulesFolder = ModuleUtil.getModuleRepository();
		for (final ModuleInfo candidate : includedOmods) {
			if (candidate.getSkipReason() != null)
				continue;
			
			// if there's an existing omod for a different version of this module, delete it
			File[] toDelete = modulesFolder.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File file, String filename) {
					return filename.startsWith(candidate.getModuleId() + "-") && filename.endsWith(".omod");
				}
			});
			if (toDelete != null && toDelete.length > 0) {
				for (File f : toDelete)
					f.delete();
			}
			try {
	            ModuleUtil.insertModuleFile(new FileInputStream(candidate.getData()), candidate.getOriginalFilename());
            }
            catch (FileNotFoundException ex) {
	            throw new RuntimeException("Programming error: file should exist (" + candidate.getData().getAbsolutePath() + ")", ex);
            }
		}
		
		List<String> ret = new ArrayList<String>();
		for (ModuleInfo info : includedOmods) {
			String line = info.getOriginalFilename() + ": ";
			if (info.getSkipReason() != null) {
				line += "skipped because " + info.getSkipReason();
			} else {
				line += "installed (" + info.getModuleId() + " version " + info.getModuleVersion() + ")";
			}
			ret.add(line);
		}
		return ret;
    }

	/**
	 * Populates skipVersion on candidate (or leaves it null if it shouldn't be skipped)
     * @param candidate
     */
    private void populateSkipReason(ModuleInfo candidate) {
    	Module existing = ModuleFactory.getModuleById(candidate.getModuleId());
	    if (existing == null)
	    	return;
	    if (!existing.isStarted())
	    	throw new RuntimeException("TODO: handle the case where the module is present but not started");

	    int test = ModuleUtil.compareVersion(candidate.getModuleVersion(), existing.getVersion());
	    if (test == 0) {
	    	candidate.setSkipReason("version " + existing.getVersion() + " is already installed");
	    } else if (test < 0) {
	    	candidate.setSkipReason("a newer version (" + existing.getVersion() + ") is already installed");
	    }
    }

	/**
     * Populates the moduleId and moduleVersion fields on candidate
     * 
     * @param candidate
	 * @throws IOException
	 *
	 * @should read the id and version from config.xml 
     */
    public void populateIdAndVersion(ModuleInfo candidate) throws IOException {
	    JarFile jar = new JarFile(candidate.getData());
	    ZipEntry entry = jar.getEntry("metadata/config.xml");
	    if (entry == null) {
	    	throw new IOException("Cannot find metadata/config.xml");
	    }
	    StringWriter sw = new StringWriter();
	    IOUtils.copy(jar.getInputStream(entry), sw);
	    String configXml = sw.toString();
	    
	    String moduleId = null;
	    {
	    	Matcher matcher = Pattern.compile("<id>(.+?)</id>").matcher(configXml);
	    	if (!matcher.find())
	    		throw new IOException("Cannot find <id>...</id> in config.xml");
	    	moduleId = matcher.group(1).trim();
	    }
	    
	    String moduleVersion = null;
	    {
	    	Matcher matcher = Pattern.compile("<version>(.+?)</version>").matcher(configXml);
	    	if (!matcher.find())
	    		throw new IOException("Cannot find <version>...</version> in config.xml");
	    	moduleVersion = matcher.group(1).trim();
	    }

	    candidate.setModuleId(moduleId);
	    candidate.setModuleVersion(moduleVersion);
    }

	/**
     * @param name
     * @return if name has any slashes, return what's after them
     */
    private String simpleFilename(String name) {
	    if (name.indexOf('/') >= 0)
	    	name = name.substring(name.indexOf('/') + 1);
	    if (name.indexOf('\\') >= 0)
	    	name = name.substring(name.indexOf('\\') + 1);
	    return name;
    }
    
    public class ModuleInfo {
    	private String originalFilename;
    	private File data;
    	private String moduleId;
    	private String moduleVersion;
    	private String skipReason;

    	/**
         * @param originalFilename
         * @param data
         */
        public ModuleInfo(String originalFilename, File data) {
	        this.originalFilename = originalFilename;
	        this.data = data;
        }
		
        /**
         * @return the originalFilename
         */
        public String getOriginalFilename() {
        	return originalFilename;
        }
		
        /**
         * @param originalFilename the originalFilename to set
         */
        public void setOriginalFilename(String originalFilename) {
        	this.originalFilename = originalFilename;
        }
		
        /**
         * @return the data
         */
        public File getData() {
        	return data;
        }
		
        /**
         * @param data the data to set
         */
        public void setData(File data) {
        	this.data = data;
        }
		
        /**
         * @return the moduleId
         */
        public String getModuleId() {
        	return moduleId;
        }
		
        /**
         * @param moduleId the moduleId to set
         */
        public void setModuleId(String moduleId) {
        	this.moduleId = moduleId;
        }
		
        /**
         * @return the moduleVersion
         */
        public String getModuleVersion() {
        	return moduleVersion;
        }

        /**
         * @param moduleVersion the moduleVersion to set
         */
        public void setModuleVersion(String moduleVersion) {
        	this.moduleVersion = moduleVersion;
        }
		
        /**
         * @return the skipReason
         */
        public String getSkipReason() {
        	return skipReason;
        }
		
        /**
         * @param skipReason the skipReason to set
         */
        public void setSkipReason(String skipReason) {
        	this.skipReason = skipReason;
        }
    	
    }
    
}