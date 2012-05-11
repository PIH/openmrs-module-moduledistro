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
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.ServletContext;

import org.apache.commons.beanutils.PropertyUtils;
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
import org.openmrs.module.web.WebModuleUtil;
import org.openmrs.util.OpenmrsUtil;

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
    public List<String> uploadDistribution(File distributionZip, ServletContext servletContext) {
    	// get all omods included in the zip file, by their original filename
    	List<UploadedModule> includedOmods = new ArrayList<DistributionServiceImpl.UploadedModule>();
		
		ZipFile zf = null;
		try {
			zf = new ZipFile(distributionZip);
			for (@SuppressWarnings("rawtypes") Enumeration e = zf.entries(); e.hasMoreElements(); ) {
				ZipEntry entry = (ZipEntry) e.nextElement();
				if (entry.getName().endsWith("/"))
					continue;
				if (!entry.getName().endsWith(".omod")) {
					throw new RuntimeException("This ZIP is only allowed to contain omod files, but this contains: " + entry.getName());
				}
				File file = File.createTempFile("distributionOmod", ".omod");
				file.deleteOnExit();
				FileUtils.copyInputStreamToFile(zf.getInputStream(entry), file);
				String originalName = simpleFilename(entry.getName());
				includedOmods.add(new UploadedModule(originalName, file));
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
		for (UploadedModule candidate : includedOmods) {
			try {
				log.info("about to inspect " + candidate);
	            populateFields(candidate);
	            log.info("inspected " + candidate);
            }
            catch (IOException ex) {
	            throw new RuntimeException("Error inspecting " + candidate.getOriginalFilename(), ex);
            }
		}
	
		// apply those actions (and log them)
		List<String> log = new ArrayList<String>();
		List<ModuleAction> actions = determineActions(includedOmods);	
		
		while (!actions.isEmpty()) {
			ModuleAction action = removeNextAction(actions);

			if (Action.SKIP.equals(action.getAction())) {
				UploadedModule info = (UploadedModule) action.getTarget();
				log.add(info.getOriginalFilename() + ": skipped because " + info.getSkipReason());
				
			} else if (Action.STOP.equals(action.getAction())) {
				Module module = (Module) action.getTarget();
				module.clearStartupError();
				List<Module> dependentModulesStopped = ModuleFactory.stopModule(module, false, true);
				for (Module depMod : dependentModulesStopped) {
					if (servletContext != null)
						WebModuleUtil.stopModule(depMod, servletContext);
					actions.add(new ModuleAction(Action.START, depMod));
					log.add("Stopped depended module " + depMod.getModuleId() + " version " + depMod.getVersion());
					
					// if any modules were stopped that we're not already planning to start, we need to start them
					if (!scheduledToStart(actions, depMod.getModuleId())) {
						actions.add(new ModuleAction(Action.START, depMod));
					}
				}
				if (servletContext != null)
					WebModuleUtil.stopModule(module, servletContext);
				log.add("Stopped " + module.getModuleId() + " version " + module.getVersion());
				
			} else if (Action.REMOVE.equals(action.getAction())) {
				Module module = (Module) action.getTarget();
				ModuleFactory.unloadModule(module);
				log.add("Removed " + module.getModuleId() + " version " + module.getVersion());
				
			} else if (Action.INSTALL.equals(action.getAction())) {
				UploadedModule info = (UploadedModule) action.getTarget();
				File inserted;
				try {
					inserted = ModuleUtil.insertModuleFile(new FileInputStream(info.getData()), info.getOriginalFilename());
				} catch (FileNotFoundException ex) {
					throw new RuntimeException("Unexpected FileNotFoundException", ex);
				}
				Module loaded = ModuleFactory.loadModule(inserted);
				log.add("Installed " + info.getModuleId() + " version " + info.getModuleVersion());
				
				// if we installed a module, we also need to start it later
				actions.add(new ModuleAction(Action.START, loaded));
				
			} else if (Action.START.equals(action.getAction())) {
				Module module = (Module) action.getTarget();
				// TODO document a core bug, that the next line does not throw the promised ModuleException
				ModuleFactory.startModule(module);
				if (module.getStartupErrorMessage() != null)
					throw new RuntimeException("Failed to start module " + module + " because of: " + module.getStartupErrorMessage());
				if (servletContext != null)
					WebModuleUtil.startModule(module, servletContext, false); // TODO figure out how to delay context refresh
				log.add("Started " + module.getModuleId() + " version " + module.getVersion());
				
			} else {
				throw new RuntimeException("Programming Error: don't know how to handle action: " + action.getAction());
			}
		}
		
		return log;
    }

	/**
     * Removes the element from the list which should executed next, and returns it.
     * (We can't just use Collections.sort, or a PriorityQueue because I don't think sorting via
     * pairwise comparison can correctly determine module startup order.)
     * 
     * @param actions
     * @return
     */
    private ModuleAction removeNextAction(List<ModuleAction> actions) {
    	if (actions.size() == 0)
    		return null;
    	
    	Collections.sort(actions, new Comparator<ModuleAction>() {
			@Override
            public int compare(ModuleAction left, ModuleAction right) {
	            return left.getAction().compareTo(right.getAction());
            }
    	});
    	
    	Action nextAction = actions.get(0).getAction();
    	if (!Action.START.equals(nextAction)) {
    		// for every category except for starting modules, order doesn't matter
    		return actions.remove(0);
    	}
    	else {
    		// find a module that has all its dependencies started already
    		for (Iterator<ModuleAction> iter = actions.iterator(); iter.hasNext(); ) {
    			ModuleAction candidate = iter.next();
    			if (candidate.getAction().equals(Action.START) && requiredModulesStarted((Module) candidate.getTarget())) {
    				iter.remove();
    				return candidate;
    			}
    		}
    		// if we couldn't find any startable modules, throw an error
    		List<String> moduleIds = new ArrayList<String>();
    		for (ModuleAction candidate : actions) {
    			if (candidate.getAction().equals(Action.START))
    				moduleIds.add(((Module) candidate.getTarget()).getModuleId());
    		}
    		throw new RuntimeException("Cannot start any of the following modules because they all depend on non-started modules: " + OpenmrsUtil.join(moduleIds, ", "));
    	}    	
    }

	/**
     * copied from ModuleFactory in 1.9.x
     */
    private boolean requiredModulesStarted(Module module) {
		for (String reqModPackage : module.getRequiredModules()) {
			boolean started = false;
			for (Module mod : ModuleFactory.getStartedModules()) {
				if (mod.getPackageName().equals(reqModPackage)) {
					String reqVersion = module.getRequiredModuleVersion(reqModPackage);
					if (reqVersion == null || ModuleUtil.compareVersion(mod.getVersion(), reqVersion) >= 0)
						started = true;
					break;
				}
			}
			
			if (!started)
				return false;
		}
		
		return true;
	}

	/**
     * @param actions
     * @param moduleId
     * @return whether actions contains a START action for the given moduleId
     */
    private boolean scheduledToStart(Collection<ModuleAction> actions, String moduleId) {
	    for (ModuleAction a : actions) {
	    	try {
		    	if (a.getAction().equals(Action.START) && PropertyUtils.getProperty(a.getTarget(), "moduleId").equals(moduleId))
		    		return true;
	    	} catch (Exception ex) {
	    		throw new RuntimeException("Cannot determine moduleId of " + a.getTarget());
	    	}
	    }
	    return false;
    }

	/**
     * Given a list of include omods, that have been inspected and their ModuleInfo fields populated, determine
     * what specific actions to take
     * 
     * @param includedOmods
     * @return
     */
    private List<ModuleAction> determineActions(List<UploadedModule> includedOmods) {
    	// using a LinkedList here because we'll treat this as a queue and remove elements from the head
	    List<ModuleAction> ret = new LinkedList<ModuleAction>();
	    
	    for (UploadedModule candidate : includedOmods) {
	    	log.info("Looking at " + candidate);
			if (Action.SKIP.equals(candidate.getAction())) {
				ret.add(new ModuleAction(Action.SKIP, candidate));
			}
			else if (Action.START.equals(candidate.getAction())) {
				ret.add(new ModuleAction(Action.START, candidate.getExisting()));
				// TODO see if we need to start any dependent modules
			}
			else if (Action.INSTALL.equals(candidate.getAction())) {
				ret.add(new ModuleAction(Action.INSTALL, candidate));
			}
			else if (Action.UPGRADE.equals(candidate.getAction())) {
				ret.add(new ModuleAction(Action.STOP, candidate.getExisting()));
				ret.add(new ModuleAction(Action.REMOVE, candidate.getExisting()));
				ret.add(new ModuleAction(Action.INSTALL, candidate));
			}
			else {
				throw new RuntimeException("Programming error: don't know how to handle action " + candidate.getAction() + " on " + candidate);
			}
		}
	    
	    return ret;
    }

	/**
     * Populates the moduleId, moduleVersion, action, and skipVersion fields on candidate
     * 
     * @param candidate
	 * @throws IOException
	 *
	 * @should read the id and version from config.xml 
     */
    public void populateFields(UploadedModule candidate) throws IOException {
	    JarFile jar = new JarFile(candidate.getData());
	    ZipEntry entry = jar.getEntry("config.xml");
	    if (entry == null) {
	    	throw new IOException("Cannot find config.xml");
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
	    
    	Module existing = ModuleFactory.getModuleById(candidate.getModuleId());
	    if (existing == null) {
	    	candidate.setAction(Action.INSTALL);
	    	return;
	    }
	    else { // there's an existing module
	    	candidate.setExisting(existing);
	    
	    	int test = ModuleUtil.compareVersion(candidate.getModuleVersion(), existing.getVersion());

		    if (test > 0) {
		    	candidate.setAction(Action.UPGRADE);
		    } else if (test == 0) {
		    	candidate.setAction(Action.SKIP);
		    	candidate.setSkipReason("version " + existing.getVersion() + " is already installed");
		    } else if (test < 0) {
		    	candidate.setAction(Action.SKIP);
		    	candidate.setSkipReason("a newer version (" + existing.getVersion() + ") is already installed");
		    }
	    
		    // if the module is up-to-date, but not running, we need to start it 
		    if (!existing.isStarted() && candidate.getAction().equals(Action.SKIP)) {
		    	candidate.setAction(Action.START);
		    }
	    }

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
    
    /**
     * The order here is important
     */
    public enum Action {
    	SKIP,
    	STOP,
    	REMOVE,
    	INSTALL,
    	START,
    	UPGRADE
    }
    
    public class ModuleAction {
    	private Action action;
    	private Object target;

    	public ModuleAction(Action action, UploadedModule target) {
    		this.action = action;
    		this.target = target;
    	}
    	
    	public ModuleAction(Action action, Module target) {
    		this.action = action;
    		this.target = target;
    	}
    	
    	/**
    	 * @see java.lang.Object#toString()
    	 */
    	@Override
    	public String toString() {
    		try {
    			return action + " " + PropertyUtils.getProperty(target, "moduleId");
    		} catch (Exception ex) {
    			return action + " " + target;
    		}
    	}
		
        /**
         * @return the action
         */
        public Action getAction() {
        	return action;
        }
		
        /**
         * @param action the action to set
         */
        public void setAction(Action action) {
        	this.action = action;
        }
		
        /**
         * @return the target
         */
        public Object getTarget() {
        	return target;
        }
		
        /**
         * @param target the target to set
         */
        public void setTarget(Object target) {
        	this.target = target;
        }
    	
    }
    
    public class UploadedModule {
    	private String originalFilename;
    	private File data;
    	private String moduleId;
    	private String moduleVersion;
    	private Module existing;
    	private Action action;
    	private String skipReason;

    	/**
         * @param originalFilename
         * @param data
         */
        public UploadedModule(String originalFilename, File data) {
	        this.originalFilename = originalFilename;
	        this.data = data;
        }
        
        /**
         * @see java.lang.Object#toString()
         */
        public String toString() {
        	StringBuilder sb = new StringBuilder();
        	sb.append(originalFilename + " (" + data.getAbsolutePath() + ") -> " + moduleId + " v" + moduleVersion + " ");
        	if (existing != null)
        		sb.append("already loaded with v" + existing.getVersion() + " ");
        	sb.append("action=" + action);
        	if (skipReason != null)
        		sb.append(" skip because " + skipReason);
        	return sb.toString();
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
		
        /**
         * @return the action
         */
        public Action getAction() {
        	return action;
        }
		
        /**
         * @param action the action to set
         */
        public void setAction(Action action) {
        	this.action = action;
        }
		
        /**
         * @return the existing
         */
        public Module getExisting() {
        	return existing;
        }
		
        /**
         * @param existing the existing to set
         */
        public void setExisting(Module existing) {
        	this.existing = existing;
        }
    	
    }
    
}