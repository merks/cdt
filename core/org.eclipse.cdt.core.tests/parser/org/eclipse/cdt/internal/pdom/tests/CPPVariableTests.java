/*******************************************************************************
 * Copyright (c) 2006, 2012 IBM Corporation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Markus Schorn (Wind River Systems)
 *******************************************************************************/

package org.eclipse.cdt.internal.pdom.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPVariable;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.internal.core.CCoreInternals;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for verifying whether the PDOM correctly stores information about
 * C++ variable declarations.
 */
public class CPPVariableTests extends PDOMTestBase {

	protected ICProject project;
	protected PDOM pdom;

	@BeforeEach
	protected void beforeEach() throws Exception {
		project = createProject("variableTests");
		pdom = (PDOM) CCoreInternals.getPDOMManager().getPDOM(project);
		pdom.acquireReadLock();
	}

	@AfterEach
	protected void afterEach() throws Exception {
		pdom.releaseReadLock();
		if (project != null) {
			project.getProject().delete(IResource.FORCE | IResource.ALWAYS_DELETE_PROJECT_CONTENT,
					new NullProgressMonitor());
		}
	}

	@Test
	public void testCPPAutoVariable() throws Exception {
		IBinding[] bindings = findQualifiedName(pdom, "autoCPPVariable");
		assertEquals(1, bindings.length);
		ICPPVariable variable = (ICPPVariable) bindings[0];
		assertFalse(variable.isExtern());
		assertFalse(variable.isStatic());
	}

	@Test
	public void testCPPExternVariable() throws Exception {
		IBinding[] bindings = findQualifiedName(pdom, "externCPPVariable");
		assertEquals(1, bindings.length);
		ICPPVariable variable = (ICPPVariable) bindings[0];
		assertTrue(variable.isExtern());
	}

	@Test
	public void testCPPRegisterVariable() throws Exception {
		IBinding[] bindings = findQualifiedName(pdom, "registerCPPVariable");
		assertEquals(1, bindings.length);
		ICPPVariable variable = (ICPPVariable) bindings[0];
	}

	@Test
	public void testCPPStaticVariable() throws Exception {
		// static elements cannot be found on global scope, see bug 161216
		IBinding[] bindings = findUnqualifiedName(pdom, "staticCPPVariable");
		assertEquals(1, bindings.length);
		ICPPVariable variable = (ICPPVariable) bindings[0];
		assertTrue(variable.isStatic());
	}
}
