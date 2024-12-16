package com.nkd.accountservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    @GetMapping("/hello-world")
    public String helloWorld() {
        return "Hello world from Service A!";
    }
}
