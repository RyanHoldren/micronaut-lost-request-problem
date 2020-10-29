package com.example;

import io.micronaut.http.annotation.*;

import static io.micronaut.http.MediaType.ALL;

@Controller
public class ExampleController {

    @Put("/upload")
    @Consumes(ALL)
    public void upload(@Body byte[] body) {

    }

    @Get("/download")
    public String download() {
        return "applesauce";
    }

}
