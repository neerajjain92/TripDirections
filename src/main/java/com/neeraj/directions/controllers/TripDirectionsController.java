package com.neeraj.directions.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

/**
 * @author neeraj on 03/10/19
 * Copyright (c) 2019, TripDirections.
 * All rights reserved.
 */
@RestController
public class TripDirectionsController {

    @GetMapping
    public String healthCheck() {
        return "Trip Directions MicroService is healthy at " + new Date();
    }
}
