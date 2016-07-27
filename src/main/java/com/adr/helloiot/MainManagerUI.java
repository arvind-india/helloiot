//    This file is part of HelloIot.
//
//    HelloIot is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    HelloIot is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with HelloIot.  If not, see <http://www.gnu.org/licenses/>.

package com.adr.helloiot;

import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import javafx.application.Application.Parameters;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

/**
 *
 * @author adrian
 */
public class MainManagerUI implements MainManager {

    private HelloIoTApp helloiotapp = null;
    private Button b = null;
    
    private Parameters params = null;
    private StackPane root = null;
    
    
    protected Properties getConfigProperties(Parameters params) {
        
        Properties config = new Properties();
        
        // read the configuration properties 
        List<String> unnamed = params.getUnnamed();    
        File configfile;
        if (unnamed.isEmpty()) {
            configfile = new File("helloiot.properties");
        } else {
            String param = unnamed.get(0);
            if (Strings.isNullOrEmpty(param)) {
                configfile = new File("helloiot.properties");
            } else {
                configfile = new File(param); 
            }
        }
        try (InputStream in = new FileInputStream(configfile)) {            
            config.load(in);
        } catch (IOException ex) {
            throw new RuntimeException("Properties file name is not correct: " + configfile.toString());
        }
        
        config.setProperty("app.exitbutton", "true");
        
        // read the parameters
        config.putAll(params.getNamed());
        
        return config;
    }    
    
    private void showMQTT() {
        
        if (b != null) {
            root.getChildren().remove(b);
            b = null;
        }
        
        Properties properties = getConfigProperties(params);
        
        ApplicationConfig config = new ApplicationConfig();
        config.mqtt_url = properties.getProperty("mqtt.url", "tcp://localhost:1883");
        config.mqtt_username = properties.getProperty("mqtt.username", "");
        config.mqtt_password = properties.getProperty("mqtt.password", "");
        config.mqtt_connectiontimeout = Integer.parseInt(properties.getProperty("mqtt.connectiontimeout", "30"));
        config.mqtt_keepaliveinterval = Integer.parseInt(properties.getProperty("mqtt.keepaliveinterval", "60"));
        config.mqtt_defaultqos =  Integer.parseInt(properties.getProperty("mqtt.defaultqos", "1"));
        config.mqtt_topicprefix =  properties.getProperty("mqtt.topicprefix", "");;
        config.mqtt_topicapp =  properties.getProperty("mqtt.topicapp", "_LOCAL_/_sys_helloIoT/mainapp");
        
        config.app_clock = Boolean.getBoolean(properties.getProperty("app.clock", "true"));
        config.app_exitbutton = Boolean.getBoolean(properties.getProperty("app.exitbutton", "true"));
        config.app_retryconnection = true;
        
        helloiotapp = new HelloIoTApp(config);
        
        // Add all devices and units
        helloiotapp.addServiceDevicesUnits();
        String devproperty = properties.getProperty("devicesunits");
        if (!Strings.isNullOrEmpty(devproperty)) {
            for (String s: devproperty.split(",")) {
                helloiotapp.addFXMLFileDevicesUnits(s);
            }
        }
    
        helloiotapp.setOnExitAction(event -> {
            root.getScene().getWindow().hide();            
        });
        
        root.getChildren().add(helloiotapp.getMQTTNode());
        helloiotapp.startAndConstruct();           
    }
    
    private void showButton() {
        if (helloiotapp != null) {
            helloiotapp.stopAndDestroy();
            root.getChildren().remove(helloiotapp.getMQTTNode());
            helloiotapp = null;     
        }

        b = new Button("PRESS ME");
        b.setOnAction(e -> {
            showMQTT();           
        });
        root.getChildren().add(b);
    }
    
    @Override
    public void construct(StackPane root, Parameters params) {
        this.root = root;
        this.params = params;
        
        showButton();
   
        
//        helloiotapp = new HelloIoTApp(getConfigProperties(params));
//        helloiotapp.getMQTTNode().setOnExitAction(event -> {
//            root.getScene().getWindow().hide();            
//        });
//        
//        root.getChildren().add(helloiotapp.getMQTTNode());
//        helloiotapp.start();        
    }
    
    @Override
    public void destroy() {  
        
        showButton();
//        helloiotapp.stopAndDestroy();
//        helloiotapp = null;
    }
}