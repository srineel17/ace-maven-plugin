package ibm.maven.plugins.ace.mojos;

import ibm.maven.plugins.ace.utils.EclipseProjectUtils;
import ibm.maven.plugins.ace.utils.ProcessOutputLogger;
import ibm.maven.plugins.ace.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * Creates a .bar file from a ace-bar Project.
 * 
 * Implemented with help from: https://github.com/TimMoore/mojo-executor/blob/master/README.md
 */

@Mojo(name = "create-bar", defaultPhase = LifecyclePhase.COMPILE)
public class CreateBarMojo extends AbstractMojo {

    /**
     * The name of the BAR (compressed file format) archive file where the
     * result is stored.
     */
    @Parameter(property = "ace.barName", defaultValue = "${project.build.directory}/ace/${project.artifactId}-${project.version}.bar", required = true)
    protected File barName;
    
    /**
     * Whether the applybaroverride command should be executed or not
     */
    @Parameter(property = "ace.applybaroverride", defaultValue = "true", required = true)
    protected Boolean applyBarOverride;

    /**
     * Refreshes the projects in the workspace and then invokes a clean build before new items are added to the BAR file.
     */
    @Parameter(property = "ace.cleanBuild", defaultValue = "true", required = true)
    protected boolean cleanBuild;

    /**
     * The name of the trace file to use when creating bar files
     */
    @Parameter(property = "ace.createBarTraceFile", defaultValue = "${project.build.directory}/createbartrace.txt", required = true)
    protected File createBarTraceFile;

    /**
     * Include "-deployAsSource" parameter?
     */
    @Parameter(property = "ace.deployAsSource", defaultValue = "true", required = true)
    protected boolean deployAsSource;

    /**
     * Compile ESQL for brokers at Version 2.1 of the product.
     */
    @Parameter(property = "ace.esql21", defaultValue = "false", required = true)
    protected boolean esql21;

    /**
     * Exclude artifacts pattern (or patterns, comma separated). By default, exclude pom.xml's as each project will have one and this causes a packaging error.
     */
    @Parameter(property = "ace.excludeArtifactsPattern", defaultValue = "**/pom.xml")
    protected String excludeArtifactsPattern;
    
    /**
     * Name of the directory to create the tmp files; required to build the project 
     */
    @Parameter(property = "ace.aceTmpDir", defaultValue = "${project.build.directory}", required = true)
    protected String aceTmpDir;
    

    /**
     * Include artifacts pattern (or patterns, comma separated). By default, the default value used for mqsipackagebar, except .esql & .subflow, which as not compilable
     * 
     * @see <a href="http://www-01.ibm.com/support/knowledgecenter/SSMKHH_9.0.0/com.ibm.etools.mft.doc/bc31720_.htm">ace9 Documentation</a>
     */
    @Parameter(property = "ace.includeArtifactsPattern", defaultValue = "**/*.xsdzip,**/*.tblxmi,**/*.xsd,**/*.wsdl,**/*.dictionary,**/*.xsl,**/*.xslt,**/*.xml,**/*.jar,**/*.inadapter,**/*.outadapter,**/*.insca,**/*.outsca,**/*.descriptor,**/*.idl,**/*.map,**/*.msgflow", required = true)
    protected String includeArtifactsPattern;

    /**
     * Projects containing files to include in the BAR file in the workspace. Required for a new workspace. A new workspace is a system folder which don't contain a .metadata folder.
     */
    @Parameter(property = "ace.projectName", defaultValue = "")
    protected String projectName;

    /*
     * Application/Service Name to add to bar file
     */

    @Parameter(property = "ace.applicationName", defaultValue = "")
    protected String applicationName;


    /**
     * Whether classloaders are in use with this bar
     */
    @Parameter(property = "ace.skipWSErrorCheck", defaultValue = "false")
    protected Boolean skipWSErrorCheck;
    
  

    /**
     * Installation directory of the ace Toolkit
     */
    @Parameter(property = "ace.toolkitInstallDir", required = true)
    protected File toolkitInstallDir;
    
    /**
     * Installation directory of the ace Toolkit
     */
    @Parameter(property = "ace.aceRunDir", required = true)
    protected File aceRunDir;

    /**
     * Major Version number of the ace Toolkit. (Current not used, but will be needed when support for difference Versions with different options is supported)
     */
    @Parameter(property = "ace.toolkitVersion", defaultValue = "10")
    protected String toolkitVersion;

    /**
     * Appends the _ (underscore) character and the value of VersionString to the names of the compiled versions of the message flows (.cmf) files added to the BAR file, before the file extension.
     */
    @Parameter(property = "ace.versionString", defaultValue = "${project.version}")
    protected String versionString;

    /**
     * The path of the workspace in which the projects are extracted to be built.
     */
    @Parameter(property = "ace.workspace", defaultValue = "${project.build.directory}/ace/workspace", required = true)
    protected File workspace;

    /**
     * Pattern (or patterns, comma separated) of jars to be excluded from the generated bar file
     */
    @Parameter(property = "ace.discardJarsPattern", defaultValue = "**/javacompute_**.jar,**/jplugin2_**.jar")
    protected String discardJarsPattern;

    /**
     * Whether classloaders are in use with this bar
     */
    @Parameter(property = "ace.useClassloaders", defaultValue = "false", required = true)
    protected Boolean useClassloaders;
    
    
    /**
     * The Maven Project Object
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The Maven Session Object
     */
    @Parameter(property = "session", required = true, readonly = true)
    protected MavenSession session;
    

    /** 
     * ibmint parameter section; added by C.Weiss, IBM 02/2022  
     * **/
    
    /**
     * Whether ibmint package should be used instead of mqsicreatebar 
     */
    @Parameter(property = "ace.ibmintPackage", defaultValue = "false", required = false)
    protected Boolean ibmintPackage;
    
    /**
     * filepath for overrides-file; set if overrides-file parameter should be used for ibmint package 
     */
    @Parameter(property = "ace.overridesFile", defaultValue = "", required = false)
    protected String overridesFile;
    
    /**
     * filepath for keywords-file; set if keywords-file parameter should be used for ibmint package 
     */
    @Parameter(property = "ace.keywordsFile", defaultValue = "", required = false)
    protected String keywordsFile;
    
    /**
     * Wheter java code should be NOT compiled  
     */
    @Parameter(property = "ace.doNotCompileJava", defaultValue = "false", required = false)
    protected Boolean doNotCompileJava;
    
    /**
     * Wheter maps and schemas should be compiled   
     */
    @Parameter(property = "ace.compileMapsAndSchemas", defaultValue = "false", required = false)
    protected Boolean compileMapsAndSchemas;
    
    


    /**
     * The Maven PluginManager Object
     */
    @Component
    protected BuildPluginManager buildPluginManager;

    private List<String> addObjectsAppsLibs() throws MojoFailureException {
        List<String> params = new ArrayList<String>();
        List<String> apps = new ArrayList<String>();
        List<String> libs = new ArrayList<String>();
        List<String> policies = new ArrayList<String>();

        // loop through the projects, adding them as "-a" Applications, "-l"
        // libraries or the deployable artefacts as "-o" objects

        // List<String> workspaceProjects = EclipseProjectUtils.getWorkspaceProjects(workspace);

        // only direct dependencies of the current bar project will be added as Applications or Libraries
        // loop through them

       //If the project is an application, add it as application else add it as library - Added below code on 08/06/2018
        //Updated the code to support PolicyProjects in ACE v11
       
        if (EclipseProjectUtils.isApplication(new File(workspace, applicationName), getLog())) {
            apps.add(applicationName);
        } else if (EclipseProjectUtils.isLibrary(new File(workspace, applicationName), getLog())) {
            libs.add(applicationName);
        }else if (EclipseProjectUtils.isPolicyProject(new File(workspace, applicationName), getLog())) {
        	policies.add(applicationName);
        }
        
        //apps.add(applicationName);
        // Changes done on 08/06/2018 complete

        for (Dependency dependency : project.getDependencies()) {

            // only check for dependencies with scope "compile" AND no standard jar files 

            if ((!dependency.getScope().equals("compile")) || (dependency.getType().equals("jar"))) {
                continue;
            }

            // the projectName is the directoryName is the artifactId
            projectName = dependency.getArtifactId();

            // Updated to exclude Shared library 
            if (EclipseProjectUtils.isApplication(new File(workspace, projectName), getLog())) {
                apps.add(projectName);
            } else if (EclipseProjectUtils.isLibrary(new File(workspace, projectName), getLog())) {
            	if (!EclipseProjectUtils.isSharedLibrary(new File(workspace, projectName), getLog())){
                libs.add(projectName);
            	}
            else if (EclipseProjectUtils.isPolicyProject(new File(workspace, projectName), getLog())) {
            	policies.add(projectName);
                }
            }
        }
        
        //create different commands 
        if (ibmintPackage) {
        	
        	//logic for ibmint package  
        	//TODO: to extend, as we can only handle one build at the moment 
            if (!apps.isEmpty()) {
            	params.add("--project");
            	params.addAll(apps);
            } 
         
            if (!libs.isEmpty()) {
                params.add("--project");
                params.addAll(libs);
            }
            
            if (!policies.isEmpty()) {
            	 params.add("--project");
                 params.addAll(policies);
             }
            
            
        } else { 
        	
        	//logic for mqsicreatebar 
        	if (!apps.isEmpty()) {
                params.add("-a");
                params.addAll(apps);
            }
        	
        	// if there are libraries, add them
            if (!libs.isEmpty()) {
                params.add("-l");
                params.addAll(libs);
            }
            
            // if there are policy projects, add them
            if (!policies.isEmpty()) {
                params.add("-x");
                params.addAll(policies);
                params.add("-p");
                params.addAll(policies);
            }
        	
        	
        }
        
          
        //Comment Christoph Weiss, IBM 16.02.2022; following code is commented; reason unknown 
        // if there are no applications and no libraries, add "unmanaged" objects
        /*
         * if (apps.isEmpty() && libs.isEmpty()) {
         * params.add("-o");
         * params.addAll(getObjectNames());
         * }
         */

        return params;
    }
    
    /*
     * constructs the parameter either for mqsicreatebar or ibmint package 
     */
    protected List<String> constructParams() throws MojoFailureException {
        List<String> params = new ArrayList<String>();

        //create the workspace 
        createWorkspaceDirectory();
        
        //add workspace to parameter 
        if (ibmintPackage) {
        	params.add("--input-path"); 
        	params.add(workspace.toString());
        	
        	params.add("--output-bar-file");
        	params.add(barName.getAbsolutePath());
        	
        	if ((overridesFile !=null) && (overridesFile.length() > 0)) {
        		params.add("--overrides-file");
        		params.add(overridesFile);
        	}
        	
        	if ((keywordsFile !=null) && (keywordsFile.length() > 0)) {
        		params.add("--keywords-file");
        		params.add(keywordsFile);
        	}
        	
        	if (doNotCompileJava) {
        		params.add("--do-not-compile-java");
        	}
        	
        	if (compileMapsAndSchemas) {
        		params.add("--compile-maps-and-schemas");
        	}
        	
        	
        } else { 
        	params.add("-data");
        	params.add(workspace.toString());
        	
        	params.add("-b");
            params.add(barName.getAbsolutePath());
            
            //cleanBuild - optional 
            if (cleanBuild) {
                params.add("-cleanBuild");
            }
            
            // use esql21 - optional
            if (esql21) {
                params.add("-esql21");
            }
            
            // deployAsSource? - optional 
            if (deployAsSource) {
                params.add("-deployAsSource");
            }
        }
        
        
        // Comment Christoph Weiss, IBM 16.02.2021: old code; unclear why it was 'commented' 
        /* if (versionString != null && versionString.length() != 0) {
            params.add("-version");
            params.add(versionString);
        }*/
        
        // project name - optional

        /*
         * params.add("-p");
         * if (projectName != null) {
         * params.add(projectName);
         * } else {
         * List<String> workspaceProjects = EclipseProjectUtils.getWorkspaceProjects(workspace);
         * 
         * params.addAll(workspaceProjects);
         * }
         */

        /*
         * params.add("-a");
         * if (applicationName != null) {
         * params.add(applicationName);
         * }
         */

        
        //call seperate method to add all project parameters 
        params.addAll(addObjectsAppsLibs());

        //TODO to validate if I need to really put the parameters to the end; 
        if (ibmintPackage) {
        	// always trace into the file target/ace/mqsicreatebartrace.txt
            params.add("--trace");
            params.add(createBarTraceFile.getAbsolutePath());
        	
        } else { 
        	//skipWSErrorCheck - option 
        	if (skipWSErrorCheck) {
                params.add("-skipWSErrorCheck");
            }
        	// always trace into the file target/ace/mqsicreatebartrace.txt
            params.add("-trace");
            params.add("-v");
            params.add(createBarTraceFile.getAbsolutePath());
        }
        
        return params;
    }

    /**
     * @throws MojoFailureException If an exception occurs
     */
    protected void createWorkspaceDirectory() throws MojoFailureException {
        if (!workspace.exists()) {
            workspace.mkdirs();
        }
        if (!workspace.isDirectory()) {
            throw new MojoFailureException(
                    "Workspace parameter is not a directory: "
                            + workspace.toString());
        }
    }

    public void execute() throws MojoFailureException, MojoExecutionException {

        getLog().info("Creating bar file: " + barName);

        File barDir = barName.getParentFile();
        if (!barDir.exists()) {
            barDir.getParentFile().mkdirs();
        }

        List<String> params = constructParams();

        executeMqsiCreateBar(params);

        try {
            // if classloaders are in use, all jars are to be removed
            if (useClassloaders) {
                getLog().info(
                        "Classloaders in use. All jars will be removed from the bar file.");
                ZipUtils.removeFiles(barName, "**/*.jar");
            } else {
                // remove the jars specified with discardJarsPattern
                if (discardJarsPattern != null
                        && !"".equals(discardJarsPattern)) {
                    getLog().info(
                            "Classloaders are not in use. The following jars will be removed from the bar file: "
                                    + discardJarsPattern);
                    ZipUtils.removeFiles(barName, discardJarsPattern);
                }
            }
        } catch (IOException e) {
            throw new MojoFailureException(
                    "Error removing jar files from bar file", e);
        }

    }

    /**
     * executes 'mqsicreatebar' or 'ibmint package' 
     * Since mqsicreatebar does something strange with
     * stdOut & stdErr, command must be written to a temporary file and executed
     * from there.
     * 
     * @param params
     * @throws MojoFailureException If an exception occurs
     */
    private void executeMqsiCreateBar(List<String> params)
            throws MojoFailureException {

        //Changes C.Weiss, IBM 02/2022: 
    	// - added aceTmpDir 
    	// - add ibmint aternative
    	// - used command handling from ValidateConfigurablePropertiesMojob 
        File cmdFile = new File(aceTmpDir
                + File.separator + "createbarCommand-" + UUID.randomUUID()
                + ".cmd");
        

        // make sure that it will be cleaned up on exit
        // cmdFile.deleteOnExit();

        
        List<String> command = new ArrayList<String>();
        String executable = new String(""); 
        String osName = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb =null;
        
        
        //select command based on pom config 
        if (ibmintPackage) { 
        	if (osName.contains("windows")) { 
        		executable = aceRunDir+"/mqsiprofile&&ibmint package";
        	} else {
        		executable = ". "+aceRunDir+"/mqsiprofile&&ibmint package";
        	}
        	
        } else {
        	executable = new String(toolkitInstallDir + File.separator + "mqsicreatebar ");
        }
        
      
        
        command.add(executable);
        command.addAll(params);

        if (getLog().isDebugEnabled()) {
        	if (osName.contains("windows")){
            getLog().debug("executing command file: " + cmdFile.getAbsolutePath());
        	}
            getLog().debug("create bar command: " + getCommandLine(command));
        }

        if (osName.contains("windows")){
            try {
                FileUtils.fileWrite(cmdFile, getCommandLine(command));

                // make sure it can be executed on Unix
                cmdFile.setExecutable(true);
            } catch (IOException e1) {
                throw new MojoFailureException("Could not create command file: " + cmdFile.getAbsolutePath());
            }
            }

        if (osName.contains("windows")){
        	pb = new ProcessBuilder(cmdFile.getAbsolutePath());
        }else if (osName.contains("linux")){
        	pb = new ProcessBuilder();
        	pb.command("bash", "-c", getCommandLine(command));
        }
        
        
        
        // redirect subprocess stderr to stdout
        pb.redirectErrorStream(true);
        Process process;
        ProcessOutputLogger stdOutHandler = null;
        try {
            pb.redirectErrorStream(true);
            process = pb.start();
            stdOutHandler = new ProcessOutputLogger(process.getInputStream(), getLog());
            stdOutHandler.start();
            process.waitFor();

        } catch (IOException e) {
            throw new MojoFailureException("Error executing: " + getCommandLine(command), e);
        } catch (InterruptedException e) {
            throw new MojoFailureException("Error executing: " + getCommandLine(command), e);
        } finally {
            if (stdOutHandler != null) {
                stdOutHandler.interrupt();
                try {
                    stdOutHandler.join();
                } catch (InterruptedException e) {
                    // this should never happen, so ignore this one
                }
            }
        }

        if (process.exitValue() != 0) {
            // logOutputFile(outFile, "error");
            throw new MojoFailureException("create bar finished with exit code: " + process.exitValue());
        }

        getLog().debug("create bar complete");

        
    }

    private String getCommandLine(List<String> command) {
        String ret = "";
        for (String element : command) {
            ret = ret.concat(" ").concat(element);
        }
        return ret;
    }

    /**
     * @return a list of objects to be (explicitly) added to the bar file
     * @throws MojoFailureException If an exception occurs
     */
    @SuppressWarnings("unchecked")
    private Collection<? extends String> getObjectNames()
            throws MojoFailureException {
        List<String> objectNames = new ArrayList<String>();

        // get the names of files under: the workspace directory, matching
        // includeFlowPatterns, not matching anything in a directory called
        // "tempfiles", excluding the base directory
        try {
            // since excludes is a regex and "\" is special for regexes, it must
            // be escaped. Not really sure if tempfiles pops up everywhere or
            // not

            String excludes = "tempfiles"
                    + (File.separator == "\\" ? "\\\\" : File.pathSeparator)
                    + "\\.*";
            if (excludeArtifactsPattern != null
                    && excludeArtifactsPattern.length() > 1) {
                excludes = excludes + "," + excludeArtifactsPattern;
            }
            objectNames = FileUtils.getFileNames(workspace,
                    includeArtifactsPattern, excludes, false);

        } catch (IOException e) {
            throw new MojoFailureException(
                    "Could not resolve includeArtifactsPattern: "
                            + includeArtifactsPattern, e);
        }

        // make sure that we found something to add to the bar file
        // if (objectNames.size() == 0) {
        // throw new MojoFailureException(
        // "Nothing matched includeFlowsPattern: "
        // + excludeArtifactsPattern
        // + " excludeArtifactsPattern: "
        // + excludeArtifactsPattern);
        // }

        return objectNames;
    }

}
