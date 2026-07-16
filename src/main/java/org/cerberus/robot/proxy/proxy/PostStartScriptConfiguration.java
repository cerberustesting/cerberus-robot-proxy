package org.cerberus.robot.proxy.proxy;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class PostStartScriptConfiguration {
    private boolean enabled = false;
    private String executable;
    private List<String> arguments = new ArrayList<>();
    private boolean waitFor = true;
    private String name;

    // Getters et Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getExecutable() { return executable; }
    public void setExecutable(String executable) { this.executable = executable; }
    public List<String> getArguments() { return arguments; }
    public void setArguments(List<String> arguments) { this.arguments = arguments; }
    public boolean isWaitFor() { return waitFor; }
    public void setWaitFor(boolean waitFor) { this.waitFor = waitFor; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

