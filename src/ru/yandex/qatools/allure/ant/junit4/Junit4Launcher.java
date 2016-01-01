package ru.yandex.qatools.allure.ant.junit4;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

public class Junit4Launcher {

	public static void main(String[] arg) throws Exception {
		ClassLoader loader = Junit4Launcher.class.getClassLoader();
		Class<?> testClass = loader.loadClass(arg[0]);
		Class<?> listenerClass = loader.loadClass(arg[1]);
		JUnitCore core= new JUnitCore();
		core.addListener((RunListener) listenerClass.newInstance());
		Result result = core.run(testClass);
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
		if (result.getFailureCount() > 0)
			System.exit(1);
	}
}
