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

  public String deployJar(MultipartFile jar, String branch) throws IOException, InterruptedException {
    Map<String, String> commands = getCommandsByBranch(branch);
    File appFile = new File(commands.get("filePath"));
    if (appFile.exists()) {
      appFile.delete();
    }
    jar.transferTo(new File(commands.get("filePath")));

    sendMessageToSlack(
        String.format(
            "Build artifact %s was successfully sent to AWS. Waiting 60 seconds for start: http://ec2-3-14-226-139.us-east-2.compute.amazonaws.com:900%d",
            commands.get("filePath"), "master".equals(branch) ? 0 : 1));
    executeChecks();
    executeShellCommand(commands.get("runScript"));
    return "successfully";
  }

  private void executeChecks() {
    //TODO implement this method
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
        "            \"after_success\": \"curl -X POST http://ec2-3-14-226-139.us-east-2.compute.amazonaws.com:9002/deploy -F 'jar=@/home/travis/build/skosinskiy/dan-it-final-project/target/final-project-0.0.1-SNAPSHOT.jar' -F 'branch=" + branch + "' --max-time 15\"\n" +
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
    //TODO move deploy check before all branches
    System.out.println(body);
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
