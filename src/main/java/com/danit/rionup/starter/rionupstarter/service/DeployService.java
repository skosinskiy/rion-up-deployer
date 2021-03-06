package com.danit.rionup.starter.rionupstarter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.json.ParseException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DeployService {

  private static final String ARTIFACTS_PATH = "/home/ubuntu/rion-up/artifacts/";
  private static final String PROD_JAR_NAME = "rion-up-application.jar";
  private static final String PROD_JAR_PATH = ARTIFACTS_PATH + PROD_JAR_NAME;
  private static final String DEV_JAR_NAME = "rion-up-application-dev.jar";
  private static final String DEV_JAR_PATH = ARTIFACTS_PATH + DEV_JAR_NAME;

  private static final String SLACK_MESSAGE_URL =
      "https://hooks.slack.com/services/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";

  private static final String TRAVIS_TOKEN = "token YNwrtvmNH9NU015vVAGHIQ";
  private static final String TRAVIS_API_VERSION = "3";
  private static final String TRAVIS_BASE_URL = "https://api.travis-ci.com/repo";
  private static final String TRAVIS_REPOSITORY_ID = "8174216";
  private static final String TRAVIS_REPOSITORY_BUILD_URL =
      String.format("%s/%s/requests", TRAVIS_BASE_URL, TRAVIS_REPOSITORY_ID);
  private static final String TRAVIS_REPOSITORY_BRANCHES_URL =
      String.format("%s/%s/branches?exists_on_github=true", TRAVIS_BASE_URL, TRAVIS_REPOSITORY_ID);

  private static final String AWS_HOST = "https://ec2-3-14-226-139.us-east-2.compute.amazonaws.com";
  private static final int MAX_CHECKS_NUMBER = 5;

  public String deployJar(MultipartFile jar, String branch) throws IOException, InterruptedException {
    sendMessageToSlack(String.format(
        "Build artifact from branch %s was successfully sent to AWS. Waiting for 1 minute to deploy.", branch));
    Map<String, String> commands = getCommandsByBranch(branch);
    transferFile(jar, commands);
    executeChecks(branch);
    executeShellCommand(commands.get("runScript"));
    return "successfully";
  }

  private void transferFile(MultipartFile jar, Map<String, String> commands) throws IOException {
    File appFile = new File(commands.get("filePath"));
    if (appFile.exists()) {
      appFile.delete();
    }
    jar.transferTo(new File(commands.get("filePath")));
  }

  private String getUrlByBranch(String branch) {
    String port = "master".equals(branch) ? "9000" : "9001";
    return String.format("%s:%s", AWS_HOST, port);
  }

  private boolean checkUrlStatusCode(String url) {
    ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.GET, null, String.class);
    return response.getStatusCode().is2xxSuccessful();
  }

  private void executeChecks(String branch) {
    String url = getUrlByBranch(branch);
    Runnable runnable = () -> {
      int checksNumber = 0;
      while (checksNumber < 5) {
        try {
          checksNumber++;
          Thread.sleep(15000);
          if (checkUrlStatusCode(url)) {
            sendMessageToSlack(String.format(
                "Application from branch %s was successfully deployed: %s", branch, url));
            checksNumber = MAX_CHECKS_NUMBER;
          }
        } catch (Exception e) {
          if (checksNumber == MAX_CHECKS_NUMBER) {
            sendMessageToSlack(String.format(
                "WARNING! Application from branch %s is unavailable after all checks!", branch));
          }
        }

      }
    };

    new Thread(runnable).start();
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

  private void executeShellCommand(String command) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command.split(" "));
    builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    Process process = builder.start();
    process.waitFor();
  }

  public List<String> getActiveTravisBranches() throws IOException {
    ResponseEntity<String> response =
        sendRequest(TRAVIS_REPOSITORY_BRANCHES_URL, HttpMethod.GET, getTravisHeaders());
    ObjectNode rootNode = new ObjectMapper().readValue(response.getBody(), ObjectNode.class);
    if (rootNode.has("branches")) {
      JsonNode branchesNode = rootNode.get("branches");
      return branchesNode.findValues("name")
          .stream()
          .map(JsonNode::asText)
          .collect(Collectors.toList())
          ;
    }
    return new ArrayList<>();
  }

  public String initTravisBuildWithDeploy(String branch) throws ParseException, IOException {
    String json = "{\n" +
        "    \"request\": {\n" +
        "        \"branch\": \"" + branch + "\",\n" +
        "        \"config\": {\n" +
        "            \"after_success\": \"curl -X POST http://ec2-3-14-226-139.us-east-2.compute.amazonaws.com:9002/deploy -F 'jar=@/home/travis/build/skosinskiy/dan-it-final-project/target/final-project-0.0.1-SNAPSHOT.jar' -F 'branch=" + branch + "'\"\n" +
        "        }\n" +
        "    }\n" +
        "}";
    ResponseEntity<String> response =
        sendRequest(TRAVIS_REPOSITORY_BUILD_URL, json, HttpMethod.POST, getTravisHeaders());
    ObjectNode rootNode = new ObjectMapper().readValue(response.getBody(), ObjectNode.class);
    if (rootNode.has("request")) {
      JsonNode requestNode = rootNode.get("request");
      if (requestNode.has("id")) {
        return requestNode.get("id").asText();
      }
    }
    return "";
  }

  public void sendMessageToSlack(String message) {
    try {
      String body = String.format("{\"text\":\"%s\"}", message);
      sendRequest(SLACK_MESSAGE_URL, body, HttpMethod.POST, getSlackHeaders());
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  private HttpHeaders getSlackHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private HttpHeaders getTravisHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", TRAVIS_TOKEN);
    headers.set("Travis-API-Version", TRAVIS_API_VERSION);
    return headers;
  }

  private ResponseEntity<String> sendRequest(
      String url,
      String body,
      HttpMethod method,
      HttpHeaders headers) throws ParseException {

    HttpEntity<Object> requestEntity = new HttpEntity<>(new JSONParser(body).parse(), headers);
    return new RestTemplate().exchange(url, method, requestEntity, String.class);
  }

  private ResponseEntity<String> sendRequest(String url, HttpMethod method, HttpHeaders headers) {
    HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
    return new RestTemplate().exchange(url, method, requestEntity, String.class);
  }

  public String getBranchFromSlackEvent(String body) throws IOException {
    ObjectNode rootNode = new ObjectMapper().readValue(body, ObjectNode.class);
    if (rootNode.has("event")) {
      JsonNode event = rootNode.get("event");
      if (event.has("type") && event.has("text")) {
        if ("message".equals(event.get("type").asText())) {
          String text = event.get("text").asText();
          String command = Arrays.stream(text.split(" ")).findFirst().orElse("");
          if ("deploy".equals(command)) {
            String branchString = Arrays.stream(text.split(" "))
                .filter(string -> string.contains("branch:"))
                .findFirst()
                .orElse("");
            return branchString.split(":")[1];
          }
        }
      }
    }
    return "";
  }

  public boolean isBranchActive(String branchName) throws IOException {
    List<String> activeTravisBranches = getActiveTravisBranches();
    return activeTravisBranches.contains(branchName);
  }

  public void initTravisBuildFromSlack(String body) {
    try {
      String branchName = getBranchFromSlackEvent(body);
      if (StringUtils.hasText(branchName)) {
        if (isBranchActive(branchName)) {
          String requestId = initTravisBuildWithDeploy(branchName);
          if (StringUtils.hasText(requestId)) {
            sendMessageToSlack(
                String.format("Travis build for branch=%s started: https://travis-ci.com/skosinskiy/dan-it-final-project/builds", branchName));
          }
        } else {
          sendMessageToSlack(
              String.format("Branch %s not found in active repository branches!", branchName));
        }
      }

    } catch (Exception e) {
      sendMessageToSlack("Failed to initialize build: " + e.getMessage());
    }
  }
}
