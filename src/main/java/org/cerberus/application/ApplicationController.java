package org.cerberus.application;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ApplicationController {
    
    
    @RequestMapping("/")
    public String index() {
        return "index.html";
    }
}
