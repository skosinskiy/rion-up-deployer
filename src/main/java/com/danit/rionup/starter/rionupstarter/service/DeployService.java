package com.danit.rionup.starter.rionupstarter.service;

import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.json.ParseException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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
  private static final String TRAVIS_REPOSITORY_BUILD = "https://api.travis-ci.com/repo/8174216/requests";

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
    ProcessBuilder builder = new ProcessBuilder(command.split(" "));
    builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process process = builder.start();
    process.waitFor();
  }

  public ResponseEntity<String> initTravisBuildWithDeploy(String branch) throws ParseException {
    String json = "{\n" +
        "    \"request\": {\n" +
        "        \"branch\": \"" + branch + "\",\n" +
        "        \"config\": {\n" +
        "            \"after_success\": \"curl -X POST http://ec2-3-14-226-139.us-east-2.compute.amazonaws.com:9002/deploy -F 'jar=@/home/travis/build/skosinskiy/dan-it-final-project/target/final-project-0.0.1-SNAPSHOT.jar' -F 'branch="+ branch + "' --max-time 15\"\n" +
        "        }\n" +
        "    }\n" +
        "}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "token YNwrtvmNH9NU015vVAGHIQ");
    headers.set("Travis-API-Version", "3");

    HttpEntity<Object> requestEntity = new HttpEntity<>(new JSONParser(json).parse(), headers);
    return new RestTemplate().postForEntity(TRAVIS_REPOSITORY_BUILD, requestEntity, String.class);
  }
}
