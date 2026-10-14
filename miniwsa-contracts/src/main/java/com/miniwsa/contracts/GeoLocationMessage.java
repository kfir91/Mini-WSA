package com.miniwsa.contracts;

import jakarta.validation.constraints.NotBlank;

public class GeoLocationMessage {

    @NotBlank
    private String country;

    @NotBlank
    private String city;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
