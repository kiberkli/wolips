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
 
package org.objectstyle.wolips.projectbuild.builder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.objectstyle.cayenne.wocompat.PropertyListSerialization;
import org.objectstyle.wolips.core.util.ExcludeIncludeMatcher;
import org.objectstyle.wolips.core.util.IStringMatcher;
import org.objectstyle.wolips.core.util.StringUtilities;
import org.objectstyle.wolips.projectbuild.WOProjectBuildConstants;
import org.objectstyle.wolips.projectbuild.natures.IncrementalNature;
import org.objectstyle.wolips.projectbuild.util.ResourceUtilities;

/**
 * @author Harald Niesche
 *
 * The incremental builder creates the build/ProjectName.woa or 
 * build/ProjectName.framework folder that contains an approximation
 * of the structure needed to run a WebObjects application or use a framework
 */
public class WOIncrementalBuilder 
  extends IncrementalProjectBuilder
  implements WOProjectBuildConstants
{

  /**
   * Constructor for WOProjectBuilder.
   */
  public WOIncrementalBuilder() {
    super();
  }
  
  /* this is duplicated from ProjectNaturePage, couldn't find a good place for now */
  private static String _getArg (Map values, String key, String defVal) {
    String result = null;
    
    try {
      result = (String)values.get(key);
    } catch (Exception up) {
      // hmm, how did that get there?
    }
    
    if (null == result) result = defVal;
    
    return result;
  }

  /**
   * @see org.eclipse.core.internal.events.InternalBuilder#build(int, Map, IProgressMonitor)
   */
  protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
    throws CoreException 
  {
    
    if (null == monitor) {
      monitor = new NullProgressMonitor ();
    }
  
    //monitor  = new SubProgressMonitor (monitor, 100*1000);
    
    monitor.beginTask("building WebObjects layout ...", 100);
    
    try {
      System.out.println(getProject());
      
      IJavaProject javaProject = getJavaProject();
      
      System.out.println(javaProject.getOutputLocation());
  
      IResourceDelta delta = getDelta(getProject());
      System.out.println(delta);
  
      WOBuildVisitor builder = new WOBuildVisitor(monitor, getProject(), args);
 
      monitor.subTask("checking directory structure ...");
 
      if (!builder._checkDirs ()) {
        delta = null;
        monitor.worked(5);
      } else if (kind == FULL_BUILD) {
        long t0 = System.currentTimeMillis();

        delta = null;
        IFolder buildFolder = getProject().getFolder("build");
        monitor.subTask("scrubbing build folder ...");
        buildFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
        monitor.worked(1);
        System.out.println("refresh build folder took: "+(System.currentTimeMillis()-t0)+" ms");
        t0 = System.currentTimeMillis();
        buildFolder.delete(true, false, null);
        monitor.worked(2);

        System.out.println("scrubbing build folder took: "+(System.currentTimeMillis()-t0)+" ms");
        t0 = System.currentTimeMillis();

        buildFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
        monitor.subTask("re-creating structure ...");
        builder._checkDirs ();
        monitor.worked(2);
        System.out.println("re-creating build folder took: "+(System.currentTimeMillis()-t0)+" ms");
      }    
      
      monitor.subTask("creating Info.plist");
     
      _createInfoPlist();
    
      monitor.worked(1);
  
      if (null != delta) {
        System.out.println("<partial build>");

        monitor.subTask("preparing partial build");
        

        BeanCounter bc = new BeanCounter(monitor, getProject());
        
        long t0 = System.currentTimeMillis();
        
        delta.accept(bc, IResourceDelta.ALL_WITH_PHANTOMS);
        
        System.out.println("visiting "+bc.count+" deltas took: "+(System.currentTimeMillis()-t0)+" ms");
        

        t0 = System.currentTimeMillis();
        

        delta.accept(builder, IResourceDelta.ALL_WITH_PHANTOMS);
        
        System.out.println("delta.accept took: "+(System.currentTimeMillis()-t0)+" ms");


        
        System.out.println("</partial build>");
        monitor.worked(12);
      } else {
        System.out.println("<full build>");
        monitor.subTask("preparing full build");
        
        BeanCounter bc = new BeanCounter(monitor, getProject());
        
        long t0 = System.currentTimeMillis();
        
        getProject().accept(bc);
        
        System.out.println("visiting "+bc.count+" project nodes took: "+(System.currentTimeMillis()-t0)+" ms");

        t0 = System.currentTimeMillis();
        getProject().accept(builder);
        System.out.println("preparing with "+bc.count+" project nodes took: "+(System.currentTimeMillis()-t0)+" ms");
        
        System.out.println("</full build>");
        monitor.worked(12);
      }

      long t0 = System.currentTimeMillis();
      builder.executeTasks(monitor);
      System.out.println("building structure took: "+(System.currentTimeMillis()-t0)+" ms");

      t0 = System.currentTimeMillis();
      monitor.subTask("copying classes");
      _jarBuild (delta, monitor);
      //System.out.println("copying classes took: "+(System.currentTimeMillis()-t0)+" ms");

      monitor.done();
    } catch (RuntimeException up) {
      up.printStackTrace();
      throw up;
    } catch (CoreException up) {
      up.printStackTrace();
      throw up;
    } 
    
    return null;
  }
  
  private void _createInfoPlist () throws CoreException {
    IProject project = getProject();
    IncrementalNature won = IncrementalNature.s_getNature(project);

    HashMap customInfo = null;

    IFile cipl = project.getFile("CustomInfo.plist");
    if (cipl.exists()) {
      try {
        Object o = PropertyListSerialization.propertyListFromFile(cipl.getLocation().toFile());
        //HashMap hash =     
        //System.out.println ("PLS.pLFS: "+o);
        if (o instanceof HashMap) {
          customInfo = (HashMap)o;        
        }
      } catch (Throwable up) {
        System.out.println("parsing CustomInfo.plist:");
        up.printStackTrace();
      }
    }
    
    
    String infoPlist;
    
    if (won.isFramework()) {
      infoPlist = INFO_PLIST_FRAMEWORK;
    } else {
      infoPlist = INFO_PLIST_APPLICATION;
    }
    
    infoPlist = StringUtilities.replace (infoPlist, "$$name$$",     won.getResultName());
    infoPlist = StringUtilities.replace (infoPlist, "$$basename$$", getProject().getName());
    infoPlist = StringUtilities.replace (infoPlist, "$$res$$",      won.getResourceName().toString());
    infoPlist = StringUtilities.replace (infoPlist, "$$wsr$$",      won.getWebResourceName().toString());
    infoPlist = StringUtilities.replace (infoPlist, "$$type$$",     won.isFramework() ? "FMWK" : "APPL");
    if (null != customInfo) {
      String principal = 
          "  <key>NSPrincipalClass</key>" + "\r\n"
        + "  <string>"+customInfo.get("NSPrincipalClass")+"</string>" + "\r\n"
      ;
      infoPlist = StringUtilities.replace (infoPlist, "$$principalclass$$", principal);
    }

    IPath infoPath = won.getInfoPath().append("Info.plist");
    IFile resFile = getProject().getWorkspace().getRoot().getFile(infoPath);
    resFile.delete(true, false, null);

    try {    
      InputStream is = new ByteArrayInputStream (infoPlist.getBytes("UTF-8"));
      
      resFile.create (is, true, null);
    } catch (UnsupportedEncodingException uee) {
      // shouldn't happen anyway, since utf8 must be supported by every JVM
      uee.printStackTrace(); 
    }
  }
    
  private void _jarBuild (IResourceDelta delta, IProgressMonitor m) throws CoreException {
    
    
    System.out.println("<jar build>");
    WOJarBuilder jarBuilder = new WOJarBuilder (m, getProject());

    long t0 = System.currentTimeMillis();

    if (null != delta) {
      delta.accept (jarBuilder, IResourceDelta.ALL_WITH_PHANTOMS);
    } else {
      IPath outPath = getJavaProject().getOutputLocation();
  
      IContainer output = getProject();
  
      if (!outPath.segment(0).equals(getProject().getName())) {
        output = getProject().getParent().getFolder(outPath);
      }
      output.accept (jarBuilder);
    }

    System.out.println ("prepare jar copy took "+(System.currentTimeMillis()-t0)+" ms");

    m.worked (10);

    t0 = System.currentTimeMillis();

    jarBuilder.executeTasks(m);    
    
    System.out.println ("executing jar copy took "+(System.currentTimeMillis()-t0)+" ms");

    System.out.println("</jar build>");
    
  }


  private IJavaProject getJavaProject () {
    try {
      return ((IJavaProject)(getProject().getNature(JavaCore.NATURE_ID)));
    } catch (CoreException up) {
    }
    return null;
  }

  /**
   * @see org.eclipse.core.resources.IncrementalProjectBuilder#startupOnInitialize()
   */
  protected void startupOnInitialize() {
    try {
      IJavaProject javaProject = getJavaProject();
      
      System.out.println(javaProject.getOutputLocation());
    } catch (Throwable up) {
    }

    
    super.startupOnInitialize();
  }

  static abstract class WOBuildHelper 
      extends    ResourceUtilities
      implements IResourceDeltaVisitor 
                , IResourceVisitor 
  {
    
    public static interface Buildtask {
      public int amountOfWork ();
      public void doWork (IProgressMonitor m) throws CoreException;
    }
    
    public static abstract class BuildtaskAbstract implements Buildtask {
      public int amountOfWork () {
        return (_workAmount);
      }
      
      protected int _workAmount = 1000;
    }
    
    public static class CopyTask extends BuildtaskAbstract {
      public CopyTask (IResource res, IPath destination, String msgPrefix) {
        _res = res;
        _dest = destination;
        _msgPrefix = msgPrefix;
        
        File file = _res.getFullPath().toFile();
        if (file.isFile()) {
          /*
          InputStream is = null;
          try {
            is = new FileInputStream (file);
            _workAmount = is.available();
          } catch (IOException up) {
          } finally {
            if (null != is) {
              try {
                is.close();
              } catch (IOException upYours) {
                // ignore
              }
            }
          }
          */
        }
      }
      
      public void doWork (IProgressMonitor m) throws CoreException {
        try {
          IContainer cont = _res.getParent();
          //cont.refreshLocal(IResource.DEPTH_INFINITE, m);

          //System.out.println (_msgPrefix+" copy "+_res+" -> "+_dest);
          
          int n = _dest.segmentCount()-3;
          IPath dstShortened = _dest;
          if (n > 0) {
            dstShortened = _dest.removeFirstSegments(n);
          }
          
          m.subTask("create " + dstShortened);
          checkDestination(_dest, m);
          _res.copy(_dest, true, null);
        } catch (CoreException up) {
          System.out.println (_msgPrefix+" *failed* to copy "+_res+" -> "+_dest+" ("+up.getMessage()+")");
//          up.printStackTrace();
//          m.setCanceled(true);
          //throw up;
          
        } catch (RuntimeException up) {
          System.out.println (_msgPrefix+" *failed* to copy "+_res+" -> "+_dest+" ("+up.getMessage()+")");
//          up.printStackTrace();
//          throw up;
        }
      }
      
      IResource _res;
      IPath     _dest;
      String    _msgPrefix;
    }

    public static class DeleteTask extends BuildtaskAbstract {
      public DeleteTask (IPath path, String msgPrefix) {
        _workAmount = 1000;
        _path = path;
        _msgPrefix = msgPrefix;
      }
      
      public void doWork (IProgressMonitor m) throws CoreException {
        if (_path == null) {
          // this really really should not happen! (again ...)
          throw new OperationCanceledException ("(deleting a null path wipes the workspace)");
        }
        
        IResource res = getWorkspaceRoot().findMember(_path);
        if (null != res) {
          res.refreshLocal (IResource.DEPTH_ONE, m);
        }
        
        IFile theFile = getWorkspaceRoot().getFile(_path);
        IContainer theFolder = getWorkspaceRoot().getFolder(_path);
        
        if (null != theFile) {
          //System.out.println (_msgPrefix+" delete "+_path);
          m.subTask("delete " + _path);
          theFile.delete(true, true, null);
        } else if (
          (null != theFolder) 
          && (theFolder instanceof IFolder)
        ) {
          System.out.println (_msgPrefix+" delete "+_path);
          m.subTask("delete " + _path);
          ((IFolder)theFolder).delete(true, true, null);
        }
        
          /*
        if (theFile.exists()) {
          if (theFile.isFile()) {
            System.out.println (_msgPrefix+" delete "+_path);
            theFile.delete();
          } else if (theFile.isDirectory()) {
            System.out.println ("*** not deleting folder: "+theFile);
          }
        }
          */
        /*
        if ((null != res) && res.exists()) {
          System.out.println (_msgPrefix+" delete "+_path);
          res.delete (true, m);
        } else {
          System.out.println (_msgPrefix+" delete (not) "+_path);
        }
        */
      }
      
      IPath _path;
      String _msgPrefix;
    }


    public WOBuildHelper (IProgressMonitor monitor, IProject project) 
      throws CoreException
    {
      _monitor = monitor;
      _project = project;
      _woNature = IncrementalNature.s_getNature(project);
    }

    /**
     * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(IResourceDelta)
     */
    public boolean visit(IResourceDelta delta) throws CoreException {
      handleResource (delta.getResource(), delta);
      return true;
    }

    /**
     * @see org.eclipse.core.resources.IResourceVisitor#visit(IResource)
     */
    public boolean visit(IResource resource) throws CoreException {
      handleResource (resource, null);
      return true;
    }

    
    public synchronized void addTask (Buildtask task) {
      _buildTasks.add(task);
      _buildWork += task.amountOfWork();
    }
    
    public void executeTasks (IProgressMonitor m)
      throws CoreException
    {
      List tasks = null;
      int amountOfWork = 0;
      synchronized (this) {
        tasks = _buildTasks;
        _buildTasks = new ArrayList ();
        amountOfWork = _buildWork;
        _buildWork = 0;
      }
      
      m = new SubProgressMonitor (m, 41);
      m.beginTask("building ...", amountOfWork);
      
      Iterator iter = tasks.iterator();

      while (iter.hasNext()) {
        Buildtask thisTask = (Buildtask)iter.next();
        
        thisTask.doWork(m);
        m.worked(thisTask.amountOfWork());
        if (m.isCanceled()) {
          throw new OperationCanceledException ();
        }
      }
      
      m.done();
    }

    
    abstract void handleResource (IResource res, IResourceDelta delta) 
      throws CoreException;

    protected IProgressMonitor _monitor;
    protected IProject _project;
    protected IncrementalNature _woNature = null;
    private List _buildTasks = new ArrayList ();
    private int  _buildWork = 0;

  }

  static class WOJarBuilder
      extends WOBuildHelper
  {
    public WOJarBuilder (IProgressMonitor monitor, IProject project) 
      throws CoreException
    {
      super (monitor, project);
      _outPath = _woNature.getJavaOutputPath();
      _compilerOutPath = _woNature.getJavaProject().getOutputLocation();
      _baseSegments = _compilerOutPath.segmentCount();
    }

    public void handleResource (IResource resource, IResourceDelta delta)
      throws CoreException
    {
      
      if (
        "class".equals(resource.getFileExtension())
      ) {
        IPath path = resource.getFullPath();
        if (_compilerOutPath.isPrefixOf(path) && !_outPath.isPrefixOf(path)) {
          boolean delete = false;
          IPath cp = path.removeFirstSegments(_baseSegments);
          path = _outPath.append(cp);
          if ((null != delta) && (delta.getKind() == IResourceDelta.REMOVED)) {
            addTask (new DeleteTask (path, "jar"));
          } else {
            addTask (new CopyTask (resource, path, "jar"));
          }
        }
      }
    }
    
    int _baseSegments;
    IPath _outPath;
    IPath _compilerOutPath;
  }


  static class WOBuildVisitor
      extends WOBuildHelper
  {
    
    WOBuildVisitor (IProgressMonitor monitor, IProject project, Map args) 
      throws CoreException 
    {
      super (monitor, project);
      
      try {
        IJavaProject jp = _woNature.getJavaProject();
        _outputPath = jp.getOutputLocation();

        _checkJavaOutputPath =  !_outputPath.equals(jp.getPath());

      } catch (CoreException up) {
        _outputPath = new Path ("/dummy");
      }
      _resMatcher = new ExcludeIncludeMatcher (
        _getArg(args, RES_EXCLUDES, RES_EXCLUDES_DEFAULT),
        _getArg(args, RES_INCLUDES, RES_INCLUDES_DEFAULT)
      );

      _wsresMatcher = new ExcludeIncludeMatcher (
        _getArg(args, WSRES_EXCLUDES, WSRES_EXCLUDES_DEFAULT),
        _getArg(args, WSRES_INCLUDES, WSRES_INCLUDES_DEFAULT)
      );
    
    }

    private boolean _checkDirs () throws CoreException {
      
      IPath buildPath  = _woNature.getBuildPath();
      IPath resultPath = _woNature.getResultPath();
      IPath resPath    = _woNature.getResourceOutputPath();
      IPath javaPath   = _woNature.getJavaOutputPath();
      IPath webresPath = _woNature.getWebResourceOutputPath();
      
      boolean result = checkDerivedDir (buildPath, null);
      result = checkDerivedDir (resPath, null) && result;
      result = checkDerivedDir (javaPath, null) && result;
      result = checkDerivedDir (webresPath, null) && result;

      _buildPath = buildPath;

      return (result);
    }


    public void handleResource (IResource res, IResourceDelta delta)  
      throws CoreException
    {
      IPath fullPath = res.getFullPath();

      // ignore resources already in build folder
      if (_buildPath.isPrefixOf(fullPath)) {
        return;
      }
      
      // ignore resources copied to the Java output folder
      if (_checkJavaOutputPath && _outputPath.isPrefixOf(fullPath)) {
        return;
      }

      String resPathString = "/"+res.getProjectRelativePath().toString();

      boolean handled = false; // mostly for debugging
      if (_resMatcher.match(resPathString)) {
        handled = _handleResource (res, delta, _woNature.asResourcePath(res.getFullPath(), res));
      } 

      if (_wsresMatcher.match(resPathString)) {
        handled = _handleResource (res, delta, _woNature.asWebResourcePath(res.getFullPath(), res));
      }
      
      if (!handled) {
        //System.out.println("//ignore: "+res);
      }
    }    
    
    public boolean _handleResource (IResource res, IResourceDelta delta, IPath copyToPath) 
      throws CoreException
    {
      if (null == copyToPath) return false;

      if ((null != delta) && (delta.getKind() == IResourceDelta.REMOVED)) {
        addTask (new DeleteTask (copyToPath, "build"));
      } else {
        addTask (new CopyTask (res, copyToPath, "build"));
      }
      
      return true;

      /*
            IPath resPath = res.getFullPath();
            if (!_outputPath.isPrefixOf(resPath)) {
              System.out.println(res);
              if (null != delta) System.out.println(delta);
            }
      */
    }
      
    
    IPath _outputPath = null;
    IPath _buildPath = null;
    boolean _checkJavaOutputPath = false;

    IStringMatcher _resMatcher;
    IStringMatcher _wsresMatcher;
    
  };


  static class BeanCounter extends WOBuildHelper {
    BeanCounter (IProgressMonitor m, IProject p) throws CoreException {
      super (m, p);
    }
    
    void handleResource (IResource res, IResourceDelta delta) {
      ++count;
    }
    
    public int count = 0;
  };

  static final String INFO_PLIST_APPLICATION = 
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "\r\n"
//+"<!DOCTYPE plist SYSTEM \"file://localhost/System/Library/DTDs/PropertyList.dtd\">" + "\r\n"
+"<plist version=\"0.9\">" + "\r\n"
+"<dict>" + "\r\n"
+"  <key>NOTE</key>" + "\r\n"
+"  <string>"  + "\r\n"
+"    Please, feel free to change this file " + "\r\n" 
+"    -- It was generated by the WOLips incremental builder and " + "\r\n" 
+"    *will be overwritten* anyway.."  + "\r\n"
+"  </string>" + "\r\n"
+"  <key>CFBundleDevelopmentRegion</key>" + "\r\n"
+"  <string>English</string>" + "\r\n"
+"  <key>CFBundleExecutable</key>" + "\r\n"
+"  <string>$$basename$$</string>" + "\r\n"
+"  <key>CFBundleIconFile</key>" + "\r\n"
+"  <string>WOAfile.icns</string>" + "\r\n"
+"  <key>CFBundleInfoDictionaryVersion</key>" + "\r\n"
+"  <string>6.0</string>" + "\r\n"
+"  <key>CFBundlePackageType</key>" + "\r\n"
+"  <string>APPL</string>" + "\r\n"
+"  <key>CFBundleSignature</key>" + "\r\n"
+"  <string>webo</string>" + "\r\n"
+"  <key>CFBundleVersion</key>" + "\r\n"
+"  <string>0.0.1d1</string>" + "\r\n"
+"  <key>NSExecutable</key>" + "\r\n"
+"  <string>$$basename$$</string>" + "\r\n"
+"  <key>NSJavaNeeded</key>" + "\r\n"
+"  <true/>" + "\r\n"
+"  <key>NSJavaPath</key>" + "\r\n"
+"  <array>" + "\r\n"
+"    <string>$$basename$$.jar</string>" + "\r\n"
+"  </array>" + "\r\n"
+"  <key>NSJavaPathClient</key>" + "\r\n"
+"  <string>$$basename$$.jar</string>" + "\r\n"
+"  <key>NSJavaRoot</key>" + "\r\n"
+"  <string>Contents/Resources/Java</string>" + "\r\n"
+"  <key>NSJavaRootClient</key>" + "\r\n"
+"  <string>Contents/WebServerResources/Java</string>" + "\r\n"
+"$$principalclass$$"
+"</dict>" + "\r\n"
+"</plist>" + "\r\n";

  static final String INFO_PLIST_FRAMEWORK = 
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "\r\n"
+"<plist version=\"0.9\">" + "\r\n"
+"<dict>" + "\r\n"
+"  <key>NOTE</key>" + "\r\n"
+"  <string>"  + "\r\n"
+"    Please, feel free to change this file " + "\r\n" 
+"    -- It was generated by the WOLips incremental builder and " + "\r\n" 
+"    *will be overwritten* anyway.."  + "\r\n"
+"  </string>" + "\r\n"
+"  <key>NSJavaPathClient</key>" + "\r\n"
+"  <string>theTestFramework.jar</string>" + "\r\n"
+"  <key>CFBundleIconFile</key>" + "\r\n"
+"  <string>WOAfile.icns</string>" + "\r\n"
+"  <key>CFBundleExecutable</key>" + "\r\n"
+"  <string>$$basename$$</string>" + "\r\n"
+"  <key>NSJavaRoot</key>" + "\r\n"
+"  <string>$$res$$/Java</string>" + "\r\n"
+"  <key>NSJavaRootClient</key>" + "\r\n"
+"  <string>$$wsr$$/Java</string>" + "\r\n"
+"  <key>NSJavaNeeded</key>" + "\r\n"
+"  <true/>" + "\r\n"
+"  <key>CFBundleName</key>" + "\r\n"
+"  <string></string>" + "\r\n"
+"  <key>NSExecutable</key>" + "\r\n"
+"  <string>$$basename$$</string>" + "\r\n"
+"  <key>NSJavaPath</key>" + "\r\n"
+"  <array>" + "\r\n"
+"    <string>$$basename$$.jar</string>" + "\r\n"
+"  </array>" + "\r\n"
+"  <key>CFBundleInfoDictionaryVersion</key>" + "\r\n"
+"  <string>6.0</string>" + "\r\n"
+"  <key>Has_WOComponents</key>" + "\r\n"
+"  <true/>" + "\r\n"
+"  <key>CFBundleSignature</key>" + "\r\n"
+"  <string>webo</string>" + "\r\n"
+"  <key>CFBundleShortVersionString</key>" + "\r\n"
+"  <string></string>" + "\r\n"
+"  <key>CFBundleIdentifier</key>" + "\r\n"
+"  <string></string>" + "\r\n"
+"  <key>CFBundlePackageType</key>" + "\r\n"
+"  <string>$$type$$</string>" + "\r\n"
+"$$principalclass$$"
+"</dict>" + "\r\n"
+"</plist>" + "\r\n";


}
