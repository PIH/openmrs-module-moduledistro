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
package org.openmrs.module.moduledistro.api;

import java.io.File;
import java.util.List;

import javax.servlet.ServletContext;

import org.openmrs.api.OpenmrsService;

/**
 * This service exposes module's core functionality. It is a Spring managed bean which is configured in moduleApplicationContext.xml.
 * <p>
 * It can be accessed only via Context:<br>
 * <code>
 * Context.getService(ModuleDistroService.class).someMethod();
 * </code>
 * 
 * @see org.openmrs.api.context.Context
 */
public interface ModuleDistroService extends OpenmrsService {

    /**
     * @param distributionZip a zip file including omods
     * @param servletContext
     * @return a log of actions taken
     * 
     * @should upload omods in a zip
     */
    List<String> uploadDistro(File distributionZip, ServletContext servletContext);

	
}