package com.mikolaj.nowak.properties_names_generator.testapp.models;

import com.google.gson.annotations.SerializedName;
import com.mikolaj.nowak.properties_names_generator.annotation.GenerateNames;

@GenerateNames//(nameAnnotations = SerializedName.class)
public class Location {
//    @SerializedName("cit")
    public String city;
    public String postalCode;
}
