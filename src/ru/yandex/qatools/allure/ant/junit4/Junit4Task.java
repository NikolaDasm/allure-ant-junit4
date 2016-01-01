package ru.yandex.qatools.allure.ant.junit4;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

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
			return name;
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
	
	private static final String launcher = "ru.yandex.qatools.allure.ant.junit4.Junit4Launcher";

	private List<ArgumentHolder> jvmArguments = new LinkedList<>();
	private List<Variable> sysProperties = new LinkedList<>();
	private List<PropertySet> sysPropertySets = new LinkedList<>();
	private List<Path> classpasses = new LinkedList<>();
	private List<Test> tests = new LinkedList<>();
	private List<BatchTest> batchTests = new LinkedList<>();
	
	private boolean fork = false;
	private Integer timeout = null;
	private boolean showOutput = false;
	private boolean newEnvironment = false;
	private boolean failonerror = false;
	private boolean haltOnFail  = false;
	private String listenerName;
	
	private static String prepareClassName(final String name) {
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
	
	public void addTest(Test test) {
		if (test.getName() == null) return;
		test.setName(prepareClassName(test.getName().trim()));
		if (test.getName().isEmpty()) return;
		tests.add(test);
	}
	
	public BatchTest createBatchTest() {
		final BatchTest test = new BatchTest(getProject());
		batchTests.add(test);
		return test;
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
	
	public void setFailonerror(final boolean failonerror) {
		this.failonerror = failonerror;
	}
	
	public void setHaltonfailure(final boolean value) {
		this.haltOnFail = value;
	}
	
	public void setListener(String listener) {
		listenerName = listener;
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
		for (String testName : allTestNames) {
			log("Run test \""+testName+"\"");
			try {
				Java javaTask = new Java();
				javaTask.setNewenvironment(newEnvironment);
				javaTask.setTaskName("junit4");
				javaTask.setProject(getProject());
				javaTask.setFork(fork);
				javaTask.setFailonerror(failonerror);
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
				taskArgs.setLine(testName+" "+listenerName);
				if (classpath.size() > 0) javaTask.setClasspath(classpath);
				javaTask.init();
				int code = javaTask.executeJava();
				if (haltOnFail && code != 0) break;
			} catch (Exception e) {
				log("Error "+e);
			}
		}
	}
}
