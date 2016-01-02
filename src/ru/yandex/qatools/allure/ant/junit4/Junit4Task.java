package ru.yandex.qatools.allure.ant.junit4;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static java.nio.file.StandardOpenOption.*;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Commandline.Argument;
import org.apache.tools.ant.types.Environment.Variable;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PropertySet;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.resources.Resources;

public class Junit4Task extends Task {
	
	public static class ArgumentHolder {
		
		private Project project;
		private String prefix = "";
		private String suffix = "";
		private String value = "";
		private String line = "";
		private boolean singleValue;
		
		public ArgumentHolder(Project project) {
			this.project = project;
		}
		
		public void setValue(String value) {
			this.value = value;
			singleValue = true;
		}
		
		public void setLine(String line) {
			this.line = line;
			singleValue = false;
		}
		
		public void setPath(Path value) {
			this.value = value.toString();
			singleValue = true;
		}
		
		public void setPathref(Reference value) {
			Path p = new Path(project);
			p.setRefid(value);
			this.value = p.toString();
			singleValue = true;
		}
		
		public void setFile(File value) {
			this.value =  value.getAbsolutePath();
			singleValue = true;
		}
		
		public void setPrefix(String prefix) {
			this.prefix = prefix != null ? prefix : "";
		}
		
		public void setSuffix(String suffix) {
			this.suffix = suffix != null ? suffix : "";
		}
		
		public void copyToArgument(Argument argument) {
			if (singleValue)
				argument.setValue(value);
			else
				argument.setLine(line);
			argument.setPrefix(prefix);
			argument.setSuffix(suffix);
		}
	}
	
	public class Test {
		private String name;
		
		public Test() {}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getName() {
			return prepareClassName((name == null) ? name : name.trim());
		}
	}
	
	public class BatchTest {
		
		private Project project;
		private Resources resources = new Resources();
		
		public BatchTest(Project project) {
			this.project = project;
			resources.setCache(true);
		}
		
		public void addFileSet(FileSet fs) {
			resources.add(fs);
			if (fs.getProject() == null) fs.setProject(project);
		}
		
		public List<String> getNames() {
			List<String> names = new LinkedList<>();
			for (Resource r : resources) {
				if (r.isExists()) {
					String pathname = r.getName();
					names.add(prepareClassName(pathname));
				}
			}
			return names;
		}
	}
	
	public class Listener {
		private String name;
		
		public Listener() {}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getName() {
			return prepareClassName((name == null) ? name : name.trim());
		}
	}
	
	private static final String launcher = "ru.yandex.qatools.allure.ant.junit4.Junit4Launcher";
	private static final String SEPARATOR = ",";
	
	private List<ArgumentHolder> jvmArguments = new LinkedList<>();
	private List<Variable> sysProperties = new LinkedList<>();
	private List<PropertySet> sysPropertySets = new LinkedList<>();
	private List<Path> classpasses = new LinkedList<>();
	private List<Test> tests = new LinkedList<>();
	private List<BatchTest> batchTests = new LinkedList<>();
	private List<Listener> listeners = new LinkedList<>();
	
	private boolean fork = false;
	private Integer timeout = null;
	private boolean showOutput = false;
	private boolean newEnvironment = false;
	private boolean failOnError = false;
	private boolean printSummary = false;
	private String parallelMode = null;
	private String poolType = null;
	private Integer poolThreads = 1;
	
	private static String prepareClassName(final String name) {
		if (name == null || name.isEmpty()) return "";
		String nameWithOutExt = (name.endsWith(".class")) ?
			name.substring(0, name.length() - ".class".length()) : name;
			return nameWithOutExt
				.replace(File.separatorChar, '.')
				.replace('/', '.')
				.replace('\\', '.');
	}
	
	public void setFork(final boolean value) {
		this.fork = value;	
	}
	
	public ArgumentHolder createJvmarg() {
		ArgumentHolder argument = new ArgumentHolder(getProject());
		jvmArguments.add(argument);
		return argument;
	}
	
	public void addConfiguredSysproperty(final Variable sysp) {
		sysProperties.add(sysp);
	}
	
	public void addSyspropertyset(final PropertySet sysp) {
		sysPropertySets.add(sysp);
	}
	
	public void addConfiguredClasspath(Path path) {
		classpasses.add(path);
	}
	
	public Test createTest() {
		final Test test = new Test();
		tests.add(test);
		return test;
	}
	
	public BatchTest createBatchTest() {
		final BatchTest test = new BatchTest(getProject());
		batchTests.add(test);
		return test;
	}

	public Listener createListener() {
		final Listener listener = new Listener();
		listeners.add(listener);
		return listener;
	}
	
	public void setTimeout(final Integer value) {
		timeout = value;
	}
	
	public void setNewenvironment(final boolean newenv) {
		newEnvironment = newenv;
	}
	
	public void setShowOutput(final boolean showOutput) {
		this.showOutput = showOutput;
	}
	
	public void setFailOnError(final boolean failOnError) {
		this.failOnError = failOnError;
	}
	
	public void setPrintSummary(final boolean printSummary) {
		this.printSummary = printSummary;
	}
	
	public void setParallelMode(final String parallelMode) {
		this.parallelMode = parallelMode;
	}
	
	public void setPoolType(final String poolType) {
		this.poolType = poolType;
	}
	
	public void setPoolThreads(final int poolThreads) {
		this.poolThreads = poolThreads;
	}
	
	@Override
	public void execute() {
		Path classpath = new Path(getProject());
		if (classpasses.size() > 0) {
			classpasses.forEach(path -> {
				classpath.add(path);
			});
		}
		List<String> allTestNames = new LinkedList<>();
		tests.forEach(test -> {
			allTestNames.add(test.getName());
		});
		batchTests.forEach(batchTest -> {
			allTestNames.addAll(batchTest.getNames());
		});
		StringBuilder testSb = new StringBuilder();
		allTestNames.forEach(name -> {
			testSb.append(name).append(SEPARATOR);
		});
		String testCases = testSb.toString();
		testCases = testCases.endsWith(SEPARATOR) ?
				testCases.substring(0, testCases.length()-1) : testCases;
		Properties conf = new Properties();
		conf.setProperty("testclasses", testCases);
		List<String> allListenerNames = new LinkedList<>();
		listeners.forEach(test -> {
			allListenerNames.add(test.getName());
		});
		StringBuilder listenerSb = new StringBuilder();
		allListenerNames.forEach(name -> {
			listenerSb.append(name).append(SEPARATOR);
		});
		String listeners = listenerSb.toString();
		listeners = listeners.endsWith(SEPARATOR) ?
			listeners.substring(0, listeners.length()-1) : listeners;
		if (!allListenerNames.isEmpty())
			conf.setProperty("listeners", listeners);
		conf.setProperty("printsummary", printSummary ? "true" : "false");
		if (parallelMode != null)
			conf.setProperty("parallelmode", parallelMode);
		if (poolType != null) {
			conf.setProperty("pool.type", poolType);
			conf.setProperty("pool.threads", poolThreads.toString());
		}
		try (OutputStream os = Files.newOutputStream(
				java.nio.file.Paths.get("junit4.properties"), CREATE)) {
			conf.store(os, null);
			log("Run JUnit tests");
			Java javaTask = new Java();
			javaTask.setNewenvironment(newEnvironment);
			javaTask.setTaskName("junit4");
			javaTask.setProject(getProject());
			javaTask.setFork(fork);
			javaTask.setFailonerror(failOnError);
			javaTask.setLogError(showOutput);
			if (timeout != null) javaTask.setTimeout((long) timeout);
			javaTask.setClassname(launcher);
			jvmArguments.forEach(argumentHolder -> {
				Argument jvmArgs = javaTask.createJvmarg();
				argumentHolder.copyToArgument(jvmArgs);
			});
			sysProperties.forEach(sysProperty -> {
				javaTask.addSysproperty(sysProperty);
			});
			sysPropertySets.forEach(sysPropertySet -> {
				javaTask.addSyspropertyset(sysPropertySet);
			});
			Argument taskArgs = javaTask.createArg();
			taskArgs.setLine("junit4.properties");
			if (classpath.size() > 0) javaTask.setClasspath(classpath);
			javaTask.init();
			javaTask.executeJava();
			java.nio.file.Path path = java.nio.file.Paths.get("junit4.properties");
			if (Files.exists(path)) Files.delete(path);
		} catch (Exception e) {
			log("Error "+e);
		}
	}
}
