package com.wol.exportapi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

	@GetMapping("/")
	public String redirectToSwagger() {
		return "redirect:/swagger-ui/index.html";
	}
}
