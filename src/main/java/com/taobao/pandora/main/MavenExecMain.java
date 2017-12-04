package com.taobao.pandora.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 *
 * <p>
 * 在 deployDirectory
 * 下面有一个mvnCommands.txt和要发布的文件，然后会执行mvnCommands.txt里的每一行命令，来进行发布。
 * deployDirectory 可以通过 -DdeployDirectory 来指定。如果没有指定，则尝试从classpath下面查找
 * mvnCommands.txt 来定位到 deployDirectory。
 * </p>
 *
 * @author duanling
 *
 */
public class MavenExecMain {

	public static final String DEPLOY_DIRECTORY_STR = "$DEPLOYDIR";

	public static final String MAVEN_COMMANDS_FILE = "mvnCommands.txt";
	public static final String DEPLOY_DIRECTORY_PROPERTIE_KEY = "deployDirectory";

	/**
	 * 尝试从classpath下面查找到要发布的目录
	 *
	 * @return
	 */
	private static File findDeployDirectoryFromClasspath() throws URISyntaxException {
		ClassLoader classLoader = MavenExecMain.class.getClassLoader();

		// 获取到存放要发布的资源的目录
		URL resource = classLoader.getResource(MAVEN_COMMANDS_FILE);
		File deployDir = null;
		if (resource != null) {
			deployDir = new File(resource.toURI()).getParentFile();
			if (deployDir.exists()) {
				System.out.println("deployDir:" + deployDir.getAbsolutePath());
				return deployDir;
			}
		}
		throw new IllegalStateException("can not find deployDir!");
	}

	/**
	 * 通过执行外部的shell的方式来获取到maven的安装目录
	 *
	 * @throws Exception
	 */
	private static void findAndSetMavenHome() throws Exception {
		String mavenHome = System.getProperty("maven.home");
		if (mavenHome != null) {
			return;
		}
		// 尝试执行外部命令来获取到maven home
		File tempScript = File.createTempFile("script", null);
		tempScript.deleteOnExit();

		Writer streamWriter = new OutputStreamWriter(new FileOutputStream(tempScript));
		PrintWriter printWriter = new PrintWriter(streamWriter);

		printWriter.println("#!/bin/bash");
		printWriter.println("export PATH=$PATH:/usr/local/bin");
		printWriter.println("source ~/.bash_profile");
		printWriter.println("mvn -version");

		printWriter.close();

		String output = new ProcessExecutor().command("bash", tempScript.getAbsolutePath()).readOutput(true).execute()
				.outputUTF8();
		System.out.println(output);
		String[] lines = StringUtils.split(output, "\r\n");
		if (lines != null) {
			for (String line : lines) {
				if (line.startsWith("Maven home:")) {
					mavenHome = StringUtils.substring(line, "Maven home:".length()).trim();
					if (StringUtils.isNotBlank(mavenHome)) {
						System.setProperty("maven.home", mavenHome);
					}
				}
			}
		}

	}

	private static String findMavenSettingPath() {
		String mavenSetting = System.getProperty("MAVEN_SETTING", System.getenv("MAVEN_SETTING"));
		return mavenSetting;
	}

	public static void main(String[] args) throws Exception {

		// System.setProperty("maven.home",
		// "/usr/local/Cellar/maven/3.3.3/libexec");
		//
		// if (System.getProperty("maven.home") == null) {
		// System.err.println("maven.home is null!");
		// }

		findAndSetMavenHome();

		// 先尝试从 -D 参数里拿到要发布的目录，如果没有拿到，则尝试从classpath下面查找到
		// 获取到存放要发布的资源的目录

		File deployDirectory = null;
		String deployDirectoryPath = System.getProperty(DEPLOY_DIRECTORY_PROPERTIE_KEY);
		if (deployDirectoryPath != null) {
			deployDirectory = new File(deployDirectoryPath);
		} else {
			deployDirectory = findDeployDirectoryFromClasspath();
		}

		FileInputStream inputStream = new FileInputStream(new File(deployDirectory, MAVEN_COMMANDS_FILE));

		List<String> lines = IOUtils.readLines(inputStream, "UTF-8");

		List<Pair<String, InvocationResult>> successReslutList = new ArrayList<Pair<String, InvocationResult>>(32);

		List<Pair<String, InvocationResult>> errorReslutList = new ArrayList<Pair<String, InvocationResult>>(32);

		String mavenSettingPath = findMavenSettingPath();

		System.out.println("mavenSettingPath: " + mavenSettingPath);

		for (String line : lines) {
			// 跳过注释
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			// 替换 $DEPLOYDIR
			if (deployDirectory != null && deployDirectory.exists()) {
				System.out.println("before replace: " + line);
				line = StringUtils.replace(line, DEPLOY_DIRECTORY_STR, deployDirectory.getAbsolutePath());
				System.out.println("before replace: " + line);
			}

			if(StringUtils.isNoneBlank(mavenSettingPath)) {
				line = " --settings " + mavenSettingPath + " " + line;
			}

			System.out.println("================start maven invoke===============================");
			System.out.println(line);

			InvocationRequest request = new DefaultInvocationRequest();

			request.setGoals(Arrays.asList(line));

			String javaHome = System.getProperty("java.home");
			if (StringUtils.isNotBlank(javaHome)) {
				request.setJavaHome(new File(javaHome));
			}

			Invoker invoker = new DefaultInvoker();
			InvocationResult invocationResult = invoker.execute(request);

			int exitCode = invocationResult.getExitCode();

			if (exitCode == 0) {
				successReslutList.add(Pair.of(line, invocationResult));
			} else {
				errorReslutList.add(Pair.of(line, invocationResult));
			}

			System.out.println("exitCode: " + exitCode);
			System.out.println("invocationResult ExecutionException: " + invocationResult.getExecutionException());
			System.out.println("================end maven invoke===============================");
		}

		System.out.println("=============== Success Result ==================");
		System.out.println("success result count: " + successReslutList.size());
		for (Pair<String, InvocationResult> pair : successReslutList) {
			System.out.println("line: " + pair.getLeft());
			System.out.println("exitCode: " + pair.getRight().getExitCode());
			System.out.println("invocationResult ExecutionException: " + pair.getRight().getExecutionException());
		}

		System.out.println("=============== Error Result ==================");
		System.out.println("error result count: " + errorReslutList.size());
		for (Pair<String, InvocationResult> pair : errorReslutList) {
			System.out.println("line: " + pair.getLeft());
			System.out.println("exitCode: " + pair.getRight().getExitCode());
			System.out.println("invocationResult ExecutionException: " + pair.getRight().getExecutionException());
		}
	}

}
