package com.danit.rionup.starter.rionupstarter.controller;

import com.danit.rionup.starter.rionupstarter.service.DeployService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

  @PostMapping("deploy/init")
  public ResponseEntity<Object> initTravisBuildFromSlack(@RequestBody(required = false) String body) {
    deployService.initTravisBuildFromSlack(body);
    return new ResponseEntity<>(HttpStatus.OK);

  }

}
