package com.stormpath.blog.spring.mvc.rest.exhandler;

/**
 * Simple User POJO used in the Spring MVC Rest Exception handling example.
 */
public class User {

    private String name;
    private String username;

    public User(){}

    public User(String name, String username) {
        this.username = username;
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
