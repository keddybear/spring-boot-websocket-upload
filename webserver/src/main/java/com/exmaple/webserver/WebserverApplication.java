package com.exmaple.webserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/*
 * Author: Dikai Xiong
 * Date: 2/10/2019
 */

@SpringBootApplication
@RestController
public class WebserverApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebserverApplication.class, args);
	}
	
	@RequestMapping(value = "/")
	public String home() {
		return "Homepage";
	}
	
	@RequestMapping(value = "/ping", method = RequestMethod.GET)
	public String version() {
		return "Version 1.0";
	}

}

