package com.neeraj.directions.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

/**
 * @author neeraj on 03/10/19
 * Copyright (c) 2019, TripDirections.
 * All rights reserved.
 */
@RestController
public class TripDirectionsController {

    private static GeoApiContext geoApiContext;
    private Logger LOGGER = LoggerFactory.getLogger(TripDirectionsController.class);
    private final static String WHITESPACE = " ";
    private final static String DOUBLE_QUOTES = "\"";
    private final static String NEW_LINE = "\n";
    private final static String UNDERSCORE = "_";
    private final static String HYPHEN = "-";

    @Value("${GOOGLE_API_KEY}")
    public void setAPIKey(String API_KEY) {
        geoApiContext = new GeoApiContext.Builder()
                .apiKey(API_KEY) // Go ahead and put your own API key.
                .build();
    }

    @GetMapping
    public String healthCheck() {
        return "Trip Directions MicroService is healthy at " + new Date();
    }

    @GetMapping("geoCode/{address}")
    public String geoCode(@PathVariable("address") String address) {
        GeocodingResult[] geocodeResults = new GeocodingResult[0];
        try {
            geocodeResults = GeocodingApi.geocode(geoApiContext,
                    address).await();
        } catch (ApiException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(geocodeResults[0]);
    }

    @GetMapping("/directions")
    public LinkedList<LatLng> getDirections(@RequestParam("source") String source,
                                            @RequestParam("destination") String destination) {
        GeocodingResult[] sourceGeoCode, destinationGeoCode;
        String sourceLatLng, destinationLatLng;
        LinkedList<LatLng> tripDirections = new LinkedList<>();

        try {
            sourceGeoCode = GeocodingApi.geocode(geoApiContext, source)
                    .await();
            destinationGeoCode = GeocodingApi.geocode(geoApiContext, destination)
                    .await();

            sourceLatLng = sourceGeoCode[0].geometry.location.toString();
            destinationLatLng = destinationGeoCode[0].geometry.location.toString();

            LOGGER.info("Latitude and Longitude for {} : {} and {} : {} respectively", source, sourceLatLng, destination, destinationLatLng);

            // Now let's fetch all coordinates between sourceLatLng and destinationLatLng

            DirectionsResult directionsResult = DirectionsApi.getDirections(geoApiContext, sourceLatLng, destinationLatLng).await();
            LOGGER.info("Directions between {} and {} is {}", source, destination, directionsResult);

            if (directionsResult != null && directionsResult.routes.length > 0) {
                DirectionsRoute directionsRoute = directionsResult.routes[0];
                if (directionsRoute.legs != null && directionsRoute.legs.length > 0) {

                    // Let's traverse all the legs
                    for (int i = 0; i < directionsRoute.legs.length; i++) { // Leg in Route
                        DirectionsLeg leg = directionsRoute.legs[i];

                        if (leg != null) {
                            for (int j = 0; j < leg.steps.length; j++) { // Steps in Leg
                                DirectionsStep step = leg.steps[j];

                                // Traverse all sub-steps;
                                if (step != null && step.steps != null && step.steps.length > 0) {
                                    for (int k = 0; k < step.steps.length; k++) { // SubSteps
                                        DirectionsStep sub_step = step.steps[k];
                                        EncodedPolyline encodedPolyline = sub_step.polyline;
                                        if (encodedPolyline != null) {
                                            // Decode Polyline and add points to the list of coordinates
                                            tripDirections.addAll(encodedPolyline.decodePath());
                                        }
                                    }
                                } else { // There are no sub-steps
                                    EncodedPolyline encodedPolyline = step.polyline;
                                    if (encodedPolyline != null) {
                                        // Decode Polyline and add points to the list of coordinates
                                        tripDirections.addAll(encodedPolyline.decodePath());
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Directions Not available
            }

        } catch (ApiException | InterruptedException | IOException e) {
            LOGGER.error("Error while fetching directions between {} and {} ==> ", source, destination, e);
        }
        return tripDirections;
    }

    @GetMapping("/directions/file")
    public ResponseEntity<Resource> getDirectionsInFile(@RequestParam("source") String source,
                                                        @RequestParam("destination") String destination,
                                                        @RequestParam("vehicleName") String vehicleName) {
        LOGGER.info("Request ==> Get Directions in File for {} and {}", source, destination);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        LinkedList<LatLng> tripDirections = getDirections(source, destination);

        if (!tripDirections.isEmpty()) {

            // Since we have the directions, let's construct the file
            try {
                StringBuffer fileName = new StringBuffer();
                fileName.append(vehicleName).append(UNDERSCORE)
                        .append(source.replaceAll(WHITESPACE, HYPHEN))
                        .append(UNDERSCORE)
                        .append(destination.replaceAll(WHITESPACE, HYPHEN))
                        .append(UNDERSCORE)
                        .append(simpleDateFormat.format(new Date()));

                File tempFile = File.createTempFile(fileName.toString(), ".txt");

                // Now write all directions to this temp file
                BufferedWriter out = new BufferedWriter(new FileWriter(tempFile));

                tripDirections.forEach(direction -> {
                    StringBuffer buffer = new StringBuffer();
                    buffer.append("lat=").append(DOUBLE_QUOTES).append(direction.lat).append(DOUBLE_QUOTES)
                            .append(WHITESPACE)
                            .append("lng=").append(DOUBLE_QUOTES).append(direction.lng).append(DOUBLE_QUOTES);
                    try {
                        out.write(buffer.toString());
                        out.write(NEW_LINE);
                    } catch (IOException e) {
                        LOGGER.error("Error while writing to the directions file {}", e);
                    }
                });
                out.close();

                InputStreamResource resource = new InputStreamResource(new FileInputStream(tempFile));
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + tempFile.getName());
                headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
                headers.add("Pragma", "no-cache");
                headers.add("Expires", "0");

                return ResponseEntity.ok()
                        .headers(headers)
                        .contentLength(tempFile.length())
                        .contentType(MediaType.parseMediaType("application/octet-stream"))
                        .body(resource);
            } catch (IOException e) {
                LOGGER.error("Error while creating directions file for {}", vehicleName);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
    }
}
