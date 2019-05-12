package com.danit.rionup.starter.rionupstarter.controller;

import com.danit.rionup.starter.rionupstarter.service.DeployService;
import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.json.ParseException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
public class DeployController {

  private DeployService deployService;

  public DeployController(DeployService deployService) {
    this.deployService = deployService;
  }

  @PostMapping("deploy")
  public String deployArtifact(@RequestParam MultipartFile jar, @RequestParam String branch) throws IOException, InterruptedException {
    return deployService.deployJar(jar, branch);
  }

  @GetMapping("deploy/init")
  public ResponseEntity<String> initTravisBuild(@RequestParam String branch) throws ParseException {
    String url = "https://api.travis-ci.com/repo/8174216/requests";
    String json = "{\n" +
        "    \"request\": {\n" +
        "        \"branch\": \"" + branch + "\",\n" +
        "        \"config\": {\n" +
        "            \"after_success\": \"curl -X POST http://ec2-3-14-226-139.us-east-2.compute.amazonaws.com:9002/deploy -F 'jar=@/home/travis/build/skosinskiy/dan-it-final-project/target/final-project-0.0.1-SNAPSHOT.jar' -F 'branch="+ branch + "'\"\n" +
        "        }\n" +
        "    }\n" +
        "}";
    System.out.println(json);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "token YNwrtvmNH9NU015vVAGHIQ");
    headers.set("Travis-API-Version", "3");

    HttpEntity<Object> requestEntity = new HttpEntity<>(new JSONParser(json).parse(), headers);
    return new RestTemplate().postForEntity(url, requestEntity, String.class);
  }

}
