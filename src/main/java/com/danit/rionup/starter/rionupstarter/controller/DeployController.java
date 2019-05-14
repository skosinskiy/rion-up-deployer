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
    return deployService.initTravisBuildWithDeploy(branch);
  }

}
