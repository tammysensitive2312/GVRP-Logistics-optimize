package org.truong.gvrp_entry_api.controller;


import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@NoArgsConstructor
public abstract class BaseController {
    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);
}
