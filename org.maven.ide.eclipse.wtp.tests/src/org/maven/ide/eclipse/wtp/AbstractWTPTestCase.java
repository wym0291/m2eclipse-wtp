/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.wtp;

import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jst.j2ee.classpathdep.IClasspathDependencyConstants;
import org.eclipse.jst.j2ee.project.facet.IJ2EEFacetConstants;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualFolder;
import org.eclipse.wst.common.componentcore.resources.IVirtualReference;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.jdt.BuildPathManager;
import org.maven.ide.eclipse.project.IProjectConfigurationManager;
import org.maven.ide.eclipse.project.ResolverConfiguration;
import org.maven.ide.eclipse.tests.common.AbstractMavenProjectTestCase;


public abstract class AbstractWTPTestCase extends AbstractMavenProjectTestCase {

  protected static final IProjectFacetVersion DEFAULT_WEB_VERSION = WebFacetUtils.WEB_FACET.getVersion("2.5");
  protected static final IProjectFacet EJB_FACET = ProjectFacetsManager.getProjectFacet(IJ2EEFacetConstants.EJB); 
  protected static final IProjectFacet UTILITY_FACET = ProjectFacetsManager.getProjectFacet(IJ2EEFacetConstants.UTILITY);
  protected static final IProjectFacetVersion UTILITY_10 = UTILITY_FACET.getVersion("1.0");
  protected static final IProjectFacet EAR_FACET = ProjectFacetsManager.getProjectFacet(IJ2EEFacetConstants.ENTERPRISE_APPLICATION);
  protected static final IProjectFacetVersion DEFAULT_EAR_FACET = IJ2EEFacetConstants.ENTERPRISE_APPLICATION_13;

  protected static IClasspathContainer getWebLibClasspathContainer(IJavaProject project) throws JavaModelException {
    IClasspathEntry[] entries = project.getRawClasspath();
    for(int i = 0; i < entries.length; i++ ) {
      IClasspathEntry entry = entries[i];
      if(entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER && "org.eclipse.jst.j2ee.internal.web.container".equals(entry.getPath().segment(0))) {
        return JavaCore.getClasspathContainer(entry.getPath(), project);
      }
    }
    return null;
  }

  private static boolean hasExtraAttribute(IClasspathEntry entry, String expectedAttribute) {
    for (IClasspathAttribute cpa : entry.getExtraAttributes()) {
      if (expectedAttribute.equals(cpa.getName())){
        return true;
      }
    }
    return false;
  }

  protected String toString(IVirtualReference[] references) {
    StringBuilder sb = new StringBuilder("[");
    
    String sep = "";
    for(IVirtualReference reference : references) {
      IVirtualComponent component = reference.getReferencedComponent();
      sb.append(sep).append(reference.getRuntimePath() + " - ");
      sb.append(component.getName());
      sb.append(" " + component.getMetaProperties());
      sep = ", ";
    }
    
    return sb.append(']').toString();
  }

  protected String toString(IFile[] files) {
    StringBuilder sb = new StringBuilder("[");
    
    String sep = "";
    for(IFile file : files) {
      sb.append(sep).append(file.getFullPath());
      sep = ", ";
    }
    
    return sb.append(']').toString();
  }

  protected void assertHasMarker(String expectedMessage, List<IMarker> markers) throws CoreException {
    Pattern p = Pattern.compile(expectedMessage);
    for (IMarker marker : markers) {
      String markerMsg = marker.getAttribute(IMarker.MESSAGE).toString(); 
      if (p.matcher(markerMsg).find()) {
        return ;
      }
    }
    fail(expectedMessage + " is not a marker");
  }

  protected void assertNotDeployable(IClasspathEntry entry) {
    assertDeployable(entry, false);
  }

  protected void assertDeployable(IClasspathEntry entry, boolean expectedDeploymentStatus) {
    //Useless : IClasspathDependencyConstants.CLASSPATH_COMPONENT_DEPENDENCY doesn't seem to be used in WTP 3.2.0. Has it ever worked???
    //assertEquals(entry.toString() + " " + IClasspathDependencyConstants.CLASSPATH_COMPONENT_DEPENDENCY, expectedDeploymentStatus,      hasExtraAttribute(entry, IClasspathDependencyConstants.CLASSPATH_COMPONENT_DEPENDENCY));
    assertEquals(entry.toString() + " " + IClasspathDependencyConstants.CLASSPATH_COMPONENT_NON_DEPENDENCY, !expectedDeploymentStatus, hasExtraAttribute(entry, IClasspathDependencyConstants.CLASSPATH_COMPONENT_NON_DEPENDENCY));
  }

  protected static IClasspathEntry[] getClassPathEntries(IProject project) throws Exception {
    IJavaProject javaProject = JavaCore.create(project);
    IClasspathContainer container = BuildPathManager.getMaven2ClasspathContainer(javaProject);
    return container.getClasspathEntries();
  }

  protected static IResource[] getUnderlyingResources(IProject project) {
    IVirtualComponent component = ComponentCore.createComponent(project);
    IVirtualFolder root = component.getRootFolder();
    IResource[] underlyingResources = root.getUnderlyingResources();
    return underlyingResources;
  }

  public AbstractWTPTestCase() {
    super();
  }

  /**
   * Replace the project pom.xml with a new one, triggers new build
   * @param project
   * @param newPomName
   * @throws Exception
   */
  protected void updateProject(IProject project, String newPomName) throws Exception {
    
    copyContent(project, newPomName, "pom.xml");
    
    IProjectConfigurationManager configurationManager = MavenPlugin.getDefault().getProjectConfigurationManager();
    ResolverConfiguration configuration = new ResolverConfiguration();
    configurationManager.enableMavenNature(project, configuration, monitor);
    configurationManager.updateProjectConfiguration(project, configuration, mavenConfiguration.getGoalOnImport(), monitor);
    
    waitForJobsToComplete();
    project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
    waitForJobsToComplete();
  }

}
