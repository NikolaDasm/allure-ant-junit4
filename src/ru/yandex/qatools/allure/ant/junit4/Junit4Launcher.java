package ru.yandex.qatools.allure.ant.junit4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunListener;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.RunnerScheduler;

public class Junit4Launcher {

	private static class ParallelComputer extends Computer {
		private final boolean classes;
		private final boolean methods;
		private final String type;
		private final int threads;
		
		public ParallelComputer(boolean classes, boolean methods, String type, int threads) {
			this.classes = classes;
			this.methods = methods;
			this.type = type;
			this.threads = threads;
		}
		
		public static Computer classes(String type, int threads) {
			return new ParallelComputer((type == null) ? false : true, false, type, threads);
		}
		
		public static Computer methods(String type, int threads) {
			return new ParallelComputer(false, (type == null) ? false : true, type, threads);
		}
		
		public static ExecutorService newPool(String type, int threads) {
			switch (type.toLowerCase()) {
				case 	"cached" : return Executors.newCachedThreadPool();
				case	"fixed" : return Executors.newFixedThreadPool(threads);
				case	"single" : return Executors.newSingleThreadExecutor();
				case	"forkjoin" : return new ForkJoinPool(threads);
				default : return ForkJoinPool.commonPool();
			}
		}
		
		private static Runner parallelize(Runner runner, String type, int threads) {
			if (runner instanceof ParentRunner) {
				((ParentRunner<?>) runner).setScheduler(new RunnerScheduler() {
					private final ExecutorService fService = newPool(type, threads);
					
					@Override
					public void schedule(Runnable childStatement) {
						fService.submit(childStatement);
					}
					
					@Override
					public void finished() {
						try {
							fService.shutdown();
							fService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
						} catch (InterruptedException e) {
								e.printStackTrace(System.err);
						}
					}
				});
					
			}
			return runner;
		}
		
		@Override
		public Runner getSuite(RunnerBuilder builder, java.lang.Class<?>[] classes)
				throws InitializationError {
			Runner suite = super.getSuite(builder, classes);
			return this.classes ? parallelize(suite, type, threads) : suite;
		}
		
		@Override
		protected Runner getRunner(RunnerBuilder builder, Class<?> testClass)
				throws Throwable {
			Runner runner = super.getRunner(builder, testClass);
			return methods ? parallelize(runner, type, threads) : runner;
		}
		
	}
	
	private static final Properties CONF = new Properties();
	private static final String JUNIY4_PROPERTIES = "junit4.properties";
	private static final String SEPARATOR = ",";

	private static String propertiesFilePath = "junit4.properties";
	
	public static void main(String[] arg) throws Exception {
		if (arg.length > 0)
			propertiesFilePath = arg[0];
		readPropertiesFileFromClasspath(JUNIY4_PROPERTIES);
		readPropertiesFile();
		if (!CONF.containsKey("testclasses"))
			throw new IllegalArgumentException("Can't find testclasses property");
		String[] testClassNames = CONF.getProperty("testclasses").split(SEPARATOR);
		ClassLoader loader = Junit4Launcher.class.getClassLoader();
		Class<?>[] testClasses = new Class<?>[testClassNames.length];
		for(int i=0; i< testClassNames.length; i++) {
			testClasses[i] = loader.loadClass(testClassNames[i]);
		}
		JUnitCore core= new JUnitCore();
		if (CONF.containsKey("listeners")) {
			String[] listenerNames = CONF.getProperty("listeners").split(SEPARATOR);
			for(int i=0; i< listenerNames.length; i++) {
				core.addListener((RunListener) loader.loadClass(listenerNames[i]).newInstance());
			}
		}
		String parallelMode = CONF.getProperty("parallelmode", "none");
		String poolType = CONF.getProperty("pool.type");
		int poolThreads = Integer.valueOf(CONF.getProperty("pool.threads", "1"));
		Computer computer = newComputer(parallelMode, poolType, poolThreads);
		Result result = core.run(computer, testClasses);
		String printSummary = CONF.getProperty("printsummary", "false");
		if (printSummary.equals("true")) {
			StringBuffer sb = new StringBuffer("Tests run: ");
			sb.append(result.getRunCount());
			sb.append(", Failures: ");
			sb.append(result.getFailureCount());
			sb.append(", Ignored: ");
			sb.append(result.getIgnoreCount());
			sb.append(", Time elapsed: ");
			sb.append(result.getRunTime()/1000);
			sb.append(" sec");
			System.out.println(sb.toString());
		}
	}
	
	private static Computer newComputer(String parallelMode, String poolType, int poolThreads) {
		switch (parallelMode.toLowerCase()) {
			case "classes" : return ParallelComputer.classes(poolType, poolThreads);
			case "methods" : return ParallelComputer.methods(poolType, poolThreads);
			default : return Computer.serial();
		}
	}
	
	private static void readPropertiesFile() throws IOException {
		Path path = Paths.get(propertiesFilePath);
		if (Files.exists(path) && !Files.isDirectory(path)) {
			try (InputStream is = Files.newInputStream(path)) {
				readPropertiesFromStream(is);
			}
		}
	}
	
	private static void readPropertiesFromStream(InputStream is) throws IOException {
		if (is == null) return;
		CONF.load(is);
	}
	
	private static void readPropertiesFileFromClasspath(String propertiesFileName) throws IOException {
		try (InputStream is = Junit4Launcher.class.getClassLoader().getResourceAsStream(propertiesFileName)) {
			readPropertiesFromStream(is);
		}
	}
}
