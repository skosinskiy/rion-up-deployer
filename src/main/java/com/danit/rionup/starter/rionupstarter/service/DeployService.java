package com.danit.rionup.starter.rionupstarter.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class DeployService {

  private static final String ARTIFACTS_PATH = "/home/ubuntu/rion-up/artifacts/";
  private static final String PROD_JAR_NAME = "rion-up-application.jar";
  private static final String PROD_JAR_PATH = ARTIFACTS_PATH + PROD_JAR_NAME;
  private static final String DEV_JAR_NAME = "rion-up-application-dev.jar";
  private static final String DEV_JAR_PATH = ARTIFACTS_PATH + DEV_JAR_NAME;

  public String deployJar(MultipartFile jar, String branch) throws IOException, InterruptedException {
    Map<String, String> commands = getCommandsByBranch(branch);
    File appFile = new File(commands.get("filePath"));
    if (appFile.exists()) {
      appFile.delete();
    }
    jar.transferTo(new File(commands.get("filePath")));

    executeCommand(commands.get("runScript"));
    return "successfully";
  }

  private Map<String, String> getCommandsByBranch(String branch) {
    HashMap<String, String> result = new HashMap<>();
    if ("master".equals(branch)) {
      result.put("filePath", PROD_JAR_PATH);
      result.put("runScript", "nohup sh /home/ubuntu/rion-up/scripts/deploy.sh &");
    } else {
      result.put("filePath", DEV_JAR_PATH);
      result.put("runScript", "nohup sh /home/ubuntu/rion-up/scripts/deploy-dev.sh &");
    }
    return result;
  }

  private void executeCommand(String command) throws IOException, InterruptedException {
    System.out.println(System.currentTimeMillis() + command);
    ProcessBuilder builder = new ProcessBuilder(command.split(" "));
    builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process process = builder.start();
    process.waitFor();
    System.out.println(System.currentTimeMillis() + process.exitValue());
  }
}
