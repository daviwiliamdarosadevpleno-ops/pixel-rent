package br.unoesc.pixelrent.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "br.unoesc.pixelrent")
public class PixelRentWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixelRentWebApplication.class, args);
    }
}