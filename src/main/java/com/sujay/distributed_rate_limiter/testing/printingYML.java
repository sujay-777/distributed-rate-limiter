package com.sujay.distributed_rate_limiter.testing;

import com.sujay.distributed_rate_limiter.configuration.TestingYML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api")
public class printingYML {

    @Autowired
     TestingYML testingYMl;

    @GetMapping("/testing")
    public ResponseEntity<String> printing(){
        return new ResponseEntity<>("Max count is " + testingYMl.getMaxCount(), HttpStatus.OK);
    }





}
