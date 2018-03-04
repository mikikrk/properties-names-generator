package com.mikolaj.nowak.properties_names_generator.testapp.models;

import com.mikolaj.nowak.properties_names_generator.annotation.GenerateNames;

@GenerateNames
public class User {
    private String name;
    private int age;
    private Location location;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
