package org.cerberus.robot.proxy.proxy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "poststart")
public class PostStartScriptProperties {

    private List<PostStartScriptConfiguration> scripts = new ArrayList<>();

    public List<PostStartScriptConfiguration> getScripts() {
        return scripts;
    }

    public void setScripts(List<PostStartScriptConfiguration> scripts) {
        this.scripts = scripts;
    }
}
