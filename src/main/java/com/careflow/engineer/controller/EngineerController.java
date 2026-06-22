package com.careflow.engineer.controller;

import com.careflow.engineer.dto.EngineerAccountRequest;
import com.careflow.engineer.service.EngineerProfileService;
import com.careflow.engineer.service.EngineerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineers")
@RequiredArgsConstructor
public class EngineerController {

    private final EngineerService engineerService;

    @PostMapping("/signup")
    public ResponseEntity<Long> engineerSignUpRequest(@Valid @RequestBody EngineerAccountRequest request){
        Long accountRequestId = engineerService.requestEngineerAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountRequestId);
    }
}
