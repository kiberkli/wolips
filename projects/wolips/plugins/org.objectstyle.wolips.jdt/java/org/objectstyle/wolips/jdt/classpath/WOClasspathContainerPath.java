/* ====================================================================
 * 
 * The ObjectStyle Group Software License, Version 1.0 
 *
 * Copyright (c) 2002 The ObjectStyle Group 
 * and individual authors of the software.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:  
 *       "This product includes software developed by the 
 *        ObjectStyle Group (http://objectstyle.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "ObjectStyle Group" and "Cayenne" 
 *    must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written 
 *    permission, please contact andrus@objectstyle.org.
 *
 * 5. Products derived from this software may not be called "ObjectStyle"
 *    nor may "ObjectStyle" appear in their names without prior written
 *    permission of the ObjectStyle Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE OBJECTSTYLE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the ObjectStyle Group.  For more
 * information on the ObjectStyle Group, please see
 * <http://objectstyle.org/>.
 *
 */

package org.objectstyle.wolips.jdt.classpath;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.graphics.Image;
import org.objectstyle.wolips.jdt.PluginImages;
import org.objectstyle.wolips.variables.VariablesPlugin;

/**
 * @author ulrich
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public final class WOClasspathContainerPath
	implements ITreeContentProvider, ILabelProvider {
	private WOClasspathContainerRoot[] roots;
	private CheckboxTreeViewer checkboxTreeViewer;
	protected ArrayList allEntries = new ArrayList();
	private boolean isExported = false;
	
	public WOClasspathContainerPath(IClasspathEntry containerEntry) {
		IPath path = null;
		if (containerEntry == null || containerEntry.getPath() == null) {
			path =
				new Path(
					WOClasspathContainer.WOLIPS_CLASSPATH_CONTAINER_IDENTITY);
			for (int i = 0;
				i
					< WOClasspathContainer
						.WOLIPS_CLASSPATH_STANDARD_FRAMEWORKS
						.length;
				i++) {
				path =
					path.append(
						"/"
							+ WOClasspathContainer
								.WOLIPS_CLASSPATH_STANDARD_FRAMEWORKS[i]);
			}
		} else {
			isExported = containerEntry.isExported();
			path = containerEntry.getPath();
		}
		String[] rootsNames = VariablesPlugin.getDefault().getFrameworkRootsNames();
		roots = new WOClasspathContainerRoot[rootsNames.length];
		for (int i = 0; i < rootsNames.length; i++) {
			roots[i] =
				new WOClasspathContainerRoot(rootsNames[i], path);
		}
	}

	public final class WOClasspathContainerRoot {
		private String root;
		private IPath path;
		private WOClasspathContainerEntry[] entries;
		protected WOClasspathContainerRoot(String root, IPath path) {
			this.root = root;
			this.path = path;
			File fwBase = new File(path.toOSString());
			if (fwBase.exists() && fwBase.isDirectory()) {
				File frameworks[] = fwBase.listFiles(new WOFWFilenameFilter());
				entries = new WOClasspathContainerEntry[frameworks.length];
				for (int i = 0; i < frameworks.length; i++) {
					WOClasspathContainerEntry entry =
						new WOClasspathContainerEntry(
							this,
							path,
							frameworks[i]);
					entries[i] = entry;
				}
			}
		}

		/*protected String getRootName() {
			return JdtPlugin.getDefault()
				.getClasspathVariablesAccessor()
				.getclasspathVariableName(
				root);
		}*/
		/**
		 * @return
		 */
		public WOClasspathContainerEntry[] getEntries() {
			return entries;
		}

		protected String getRoot() {
			return root;
		}
		
		protected IPath getPath() {
			return path;
		}

	}

	public final class WOClasspathContainerEntry {
		private WOClasspathContainerRoot root;
		private boolean checked = false;
		private String name;
		protected WOClasspathContainerEntry(
			WOClasspathContainerRoot root,
			IPath path,
			File framework) {
			this.root = root;
			if (path != null) {
				String[] segments = path.segments();
				name = framework.getName();
				//				cut off the .framework
				name = name.substring(0, name.length() - 10);
				for (int i = 0; i < segments.length; i++) {
					checked =
						(i > 0
							&& !allEntries.contains(name)
							&& segments[i].equals(name)
							&& this.exists(
								segments[i],
								(this.getRoot().getPath())));
					if (checked) {
						i = segments.length;
						allEntries.add(name);
					}

				}
			}
		}

		private boolean exists(String framework, IPath rootPath) {
			File frameworkFile =
					new File(
							rootPath.toOSString(),
						framework + ".framework/Resources/Java");
				return frameworkFile.isDirectory();
		}
		/**
		 * @return
		 */
		public boolean isChecked() {
			return checked;
		}

		/**
		 * @param b
		 */
		protected void setChecked(boolean b) {
			checked = b;
		}

		protected String getName() {
			return name;
		}

		protected WOClasspathContainerRoot getRoot() {
			return root;
		}

	}

	private static final class WildcardFilenameFilter
		implements FilenameFilter {
		WildcardFilenameFilter(String prefix, String suffix) {
			_prefix = prefix;
			_suffix = suffix;
		}

		public boolean accept(File file, String name) {

			String lowerName = name.toLowerCase();

			return (
				((null == _prefix) || lowerName.startsWith(_prefix))
					&& ((null == _suffix) || lowerName.endsWith(_suffix)));
		}

		String _prefix;
		String _suffix;
	}

	private static final class WOFWFilenameFilter implements FilenameFilter {
		public boolean accept(File file, String name) {
			/*name.startsWith("Java") &&*/
			boolean candidate = name.endsWith(".framework");

			boolean result = false;

			if (candidate) {
				File resDir = new File(file, name + "/Resources/Java");
				if (resDir.exists()) {

					String jarFiles[] =
						resDir.list(new WildcardFilenameFilter(null, ".jar"));
					String zipFiles[] =
						resDir.list(new WildcardFilenameFilter(null, ".zip"));

					result = (0 != jarFiles.length) || (0 != zipFiles.length);

				}
			}

			return (result);
		}
	}

	/**
	 * @return
	 */
	public IClasspathEntry getClasspathEntry() {
		IPath path = null;
		path =
			new Path(WOClasspathContainer.WOLIPS_CLASSPATH_CONTAINER_IDENTITY);
		if (this.getRoots() != null)
			for (int i = 0; i < this.getRoots().length; i++) {
				WOClasspathContainerRoot root = this.getRoots()[i];
				if (root.getEntries() != null)
					for (int j = 0; j < root.getEntries().length; j++) {
						if (root.getEntries() != null
							&& checkboxTreeViewer.getChecked(root.getEntries()[j])) {
							//path = path.append(root.root);
							path = path.append(root.getEntries()[j].getName());
						}
					}
			}
		return JavaCore.newContainerEntry(path, isExported);
	}

	/**
	 * @return
	 */
	public WOClasspathContainerRoot[] getRoots() {
		return roots;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof WOClasspathContainerRoot) {
			WOClasspathContainerEntry[] entries =
				((WOClasspathContainerRoot) parentElement).getEntries();
			/*
			for (int i = 0; i < entries.length; i++) {
			viewer.setChecked(entries[i], entries[i].isChecked());
			}*/
			return entries;
		}
		if (parentElement instanceof WOClasspathContainerPath) {
			WOClasspathContainerRoot[] currentRoots =
				((WOClasspathContainerPath) parentElement).getRoots();
			/*
			for (int i = 0; i < roots.length; i++) {
			viewer.add(roots[i], this.getChildren(roots[i]));
			}*/
			return currentRoots;
		}
		return new Object[0];
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
	 */
	public Object getParent(Object element) {
		if (element instanceof WOClasspathContainerRoot)
			return this;
		if (element instanceof WOClasspathContainerEntry)
			return ((WOClasspathContainerEntry) element).getRoot();
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
	 */
	public boolean hasChildren(Object element) {
		if (element instanceof WOClasspathContainerPath)
			return ((WOClasspathContainerPath) element).getRoots() != null
				&& ((WOClasspathContainerPath) element).getRoots().length > 0;
		if (element instanceof WOClasspathContainerRoot)
			return ((WOClasspathContainerRoot) element).getEntries() != null
				&& ((WOClasspathContainerRoot) element).getEntries().length > 0;
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
	 */
	public Object[] getElements(Object inputElement) {
		return this.getChildren(inputElement);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
	 */
	public void dispose() {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		this.checkboxTreeViewer = (CheckboxTreeViewer) viewer;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ILabelProvider#getImage(java.lang.Object)
	 */
	public Image getImage(Object element) {
		if (element instanceof WOClasspathContainerRoot)
			return PluginImages.WOFRAMEWORK_ROOT_IMAGE();
		if (element instanceof WOClasspathContainerEntry) {
			for (int i = 0;
				i
					< WOClasspathContainer
						.WOLIPS_CLASSPATH_STANDARD_FRAMEWORKS
						.length;
				i++) {
				if (WOClasspathContainer
					.WOLIPS_CLASSPATH_STANDARD_FRAMEWORKS[i]
					.equals(((WOClasspathContainerEntry) element).getName()))
					return PluginImages.WOSTANDARD_FRAMEWORK_IMAGE();
			}
			return PluginImages.WOFRAMEWORK_IMAGE();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ILabelProvider#getText(java.lang.Object)
	 */
	public String getText(Object element) {
		if (element instanceof WOClasspathContainerRoot)
			return ((WOClasspathContainerRoot) element).getRoot();
		if (element instanceof WOClasspathContainerEntry)
			return ((WOClasspathContainerEntry) element).getName();

		return element.toString();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IBaseLabelProvider#addListener(org.eclipse.jface.viewers.ILabelProviderListener)
	 */
	public void addListener(ILabelProviderListener listener) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IBaseLabelProvider#isLabelProperty(java.lang.Object, java.lang.String)
	 */
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IBaseLabelProvider#removeListener(org.eclipse.jface.viewers.ILabelProviderListener)
	 */
	public void removeListener(ILabelProviderListener listener) {
	}

}
