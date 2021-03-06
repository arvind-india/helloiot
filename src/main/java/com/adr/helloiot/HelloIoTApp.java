//    HelloIoT is a dashboard creator for MQTT
//    Copyright (C) 2017-2018 Adrián Romero Corchado.
//
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
//
package com.adr.helloiot;

import com.adr.helloiotlib.app.IoTApp;
import com.adr.helloiot.mqtt.ManagerMQTT;
import com.adr.fonticon.FontAwesome;
import com.adr.fonticon.IconBuilder;
import com.adr.fonticon.decorator.FillPaint;
import com.adr.fonticon.decorator.Shine;
import com.adr.hellocommon.dialog.DialogException;
import com.adr.hellocommon.dialog.DialogView;
import com.adr.hellocommon.dialog.MessageUtils;
import com.adr.helloiotlib.unit.Unit;
import com.adr.helloiotlib.device.Device;
import com.adr.helloiot.device.DeviceSimple;
import com.adr.helloiot.device.DeviceSwitch;
import com.adr.helloiotlib.device.ListDevice;
import com.adr.helloiot.device.TreePublish;
import com.adr.helloiot.device.TreePublishSubscribe;
import com.adr.helloiotlib.format.MiniVar;
import com.adr.helloiot.media.SilentClipFactory;
import com.adr.helloiot.media.StandardClipFactory;
import com.adr.helloiot.mqtt.MQTTProperty;
import com.adr.helloiot.tradfri.ManagerTradfri;
import com.adr.helloiot.unit.UnitPage;
import com.adr.helloiot.util.CompletableAsync;
import com.adr.helloiot.util.HTTPUtils;
import com.adr.helloiotlib.format.MiniVarBoolean;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 *
 * @author adrian
 */
public class HelloIoTApp implements IoTApp {

    private final static Logger LOGGER = Logger.getLogger(HelloIoTApp.class.getName());

    private final List<UnitPage> appunitpages = new ArrayList<>();
    private final List<Unit> appunits = new ArrayList<>();
    private final List<Device> appdevices = new ArrayList<>();

    private final ApplicationTopicsManager topicsmanager;
    private final MainNode mainnode;
    private final ResourceBundle resources;

    private EventHandler<ActionEvent> exitevent = null;
    private final Runnable styleConnection;

    public HelloIoTApp(Map<String, MiniVar> config) throws HelloIoTException {

        // Load resources
        resources = ResourceBundle.getBundle("com/adr/helloiot/fxml/main");

        // System and Application devices units
        addSystemDevicesUnits(config.get("client.topicsys").asString());
        addAppDevicesUnits(config.get("client.topicapp").asString());

        ManagerComposed manager = new ManagerComposed();
        manager.addManagerProtocol(
                "_LOCAL_/",
                new ManagerLocal(
                        config.get("client.topicapp").asString()));

        if (HTTPUtils.getAddress(config.get("tradfri.host").asString()) != null) {
            manager.addManagerProtocol(
                    "TRÅDFRI/",
                    new ManagerTradfri(
                            config.get("tradfri.host").asString(),
                            config.get("tradfri.identity").asString(),
                            config.get("tradfri.psk").asString()));
        }

        if (HTTPUtils.getAddress(config.get("mqtt.host").asString()) != null) {
            boolean websockets = config.get("mqtt.websockets").asBoolean();
            boolean ssl = config.get("mqtt.ssl").asBoolean();
            String protocol = websockets
                    ? (ssl ? "wss" : "ws")
                    : (ssl ? "ssl" : "tcp");
            Properties sslproperties;
            if (ssl) {
                sslproperties = new Properties();
                sslproperties.setProperty("com.ibm.ssl.protocol", config.get("mqtt.protocol").asString());
                if (!config.get("mqtt.keystore").isEmpty()) {
                    sslproperties.setProperty("com.ibm.ssl.keyStore", config.get("mqtt.keystore").asString());
                    sslproperties.setProperty("com.ibm.ssl.keyStorePassword", config.get("mqtt.keystorepassword").asString());
                    sslproperties.setProperty("com.ibm.ssl.keyStoreType", "JKS");                    
                }
                if (!config.get("mqtt.truststore").isEmpty()) {
                    sslproperties.setProperty("com.ibm.ssl.trustStore", config.get("mqtt.truststore").asString());
                    sslproperties.setProperty("com.ibm.ssl.trustStorePassword", config.get("mqtt.truststorepassword").asString());
                    sslproperties.setProperty("com.ibm.ssl.trustStoreType", "JKS");
                }
            } else {
                sslproperties = null;
            }
            String mqtturl = protocol + "://" + config.get("mqtt.host").asString() + ":" + Integer.toString(config.get("mqtt.port").asInt());
            manager.addManagerProtocol(
                    "",
                    new ManagerMQTT(
                            mqtturl,
                            config.get("mqtt.username").asString(),
                            config.get("mqtt.password").asString(),
                            config.get("mqtt.clientid").asString(),
                            config.get("mqtt.connectiontimeout").asInt(),
                            config.get("mqtt.keepaliveinterval").asInt(),
                            config.get("mqtt.version").asInt(),
                            config.get("mqtt.maxinflight").asInt(),
                            config.get("client.topicsys").asString(),
                            sslproperties));
            
            // Broker panel
            if ("1".equals(config.get("client.broker").asString())) {
                UnitPage info = new UnitPage("info", IconBuilder.create(FontAwesome.FA_INFO, 24.0).styleClass("icon-fill").build(), resources.getString("page.info"));
                addUnitPages(Arrays.asList(info));
                addFXMLFileDevicesUnits("local:com/adr/helloiot/panes/mosquitto");
            }          
        }

        styleConnection = config.get("app.retryconnection").asBoolean() ? this::tryConnection : this::oneConnection;

        mainnode = new MainNode(
                this,
                Platform.isSupported(ConditionalFeature.MEDIA) ? new StandardClipFactory() : new SilentClipFactory(),
                config.get("app.clock").asBoolean(),
                config.get("app.exitbutton").asBoolean());

        topicsmanager = new ApplicationTopicsManager(manager);
        topicsmanager.setOnConnectionLost(t -> {
            LOGGER.log(Level.WARNING, "Connection lost to broker.", t);
            Platform.runLater(() -> {
                mainnode.stop();
                CompletableAsync.handle(topicsmanager.close(), 
                    v -> {
                        ultraConnection(3, Duration.seconds(2.5));
                    },
                    ex -> {
                        showConnectionException(t);
                    });
            });
        });
    }

    public void addUnitPages(List<UnitPage> unitpages) {
        appunitpages.addAll(unitpages);
    }

    public void addDevicesUnits(List<Device> devices, List<Unit> units) {
        appdevices.addAll(devices);
        appunits.addAll(units);
    }

    public void addServiceDevicesUnits() {
        ServiceLoader<ApplicationDevicesUnits> devicesunitsloader = ServiceLoader.load(ApplicationDevicesUnits.class);
        for (ApplicationDevicesUnits c : devicesunitsloader) {
            addDevicesUnits(c.getDevices(), c.getUnits());
        }
    }

    private void addAppDevicesUnits(String topicapp) {

        DeviceSimple unitpage = new DeviceSimple();
        unitpage.setTopic(topicapp + "/unitpage");
        unitpage.setId(SYS_UNITPAGE_ID);

        DeviceSwitch beeper = new DeviceSwitch();
        beeper.setTopic(topicapp + "/beeper");
        beeper.setId(SYS_BEEPER_ID);

        DeviceSimple buzzer = new DeviceSimple();
        MQTTProperty.setRetained(buzzer, false);
        buzzer.setTopic(topicapp + "/buzzer");
        buzzer.setId(SYS_BUZZER_ID);

        addDevicesUnits(Arrays.asList(unitpage, beeper, buzzer), Collections.emptyList());
    }

    private void addSystemDevicesUnits(String topicsys) {
        TreePublish sysevents = new TreePublish();
        sysevents.setTopic(topicsys + "/events");
        sysevents.setId(SYS_EVENT_ID);

        TreePublishSubscribe sysstatus = new TreePublishSubscribe();
        sysstatus.setTopic(topicsys + "/status");
        sysstatus.setId(SYS_VALUE_ID);

        addDevicesUnits(Arrays.asList(sysevents, sysstatus), Collections.emptyList());
    }

    public void addFXMLFileDevicesUnits(String filedescriptor) throws HelloIoTException {

        try {
            String versionfxml = HelloPlatform.getInstance().isPhone() ? "_mobile.fxml" : ".fxml";
            URL fxmlurl;
            ResourceBundle fxmlresources;
            if (filedescriptor.startsWith("local:")) {
                // Is a local resource
                fxmlurl = HelloIoTApp.class.getResource("/" + filedescriptor.substring(6) + versionfxml);
                fxmlresources = ResourceBundle.getBundle(filedescriptor.substring(6));
            } else {
                // Is a file       
                fxmlurl = new File(filedescriptor + versionfxml).toURI().toURL();
                File file = new File(filedescriptor);
                URL[] urls = {file.getAbsoluteFile().getParentFile().toURI().toURL()};
                ClassLoader loader = new URLClassLoader(urls);
                fxmlresources = ResourceBundle.getBundle(file.getName(), Locale.getDefault(), loader);
            }

            FXMLLoader fxmlloader;
            fxmlloader = new FXMLLoader(fxmlurl);
            if (fxmlresources != null) {
                fxmlloader.setResources(fxmlresources);
            }
            DevicesUnits du = fxmlloader.load();
            addDevicesUnits(du.getDevices(), du.getUnits());
        } catch (IOException | MissingResourceException ex) {
            throw new HelloIoTException(String.format(resources.getString("exception.cannotloadfxml"), filedescriptor), ex);
        }
    }

    public void setOnDisconnectAction(EventHandler<ActionEvent> exitevent) {
        this.exitevent = exitevent;
    }

    public void startAndConstruct() {

        // External services
        ServiceLoader<ApplicationUnitPages> unitpagesloader = ServiceLoader.load(ApplicationUnitPages.class);
        for (ApplicationUnitPages c : unitpagesloader) {
            appunitpages.addAll(c.getUnitPages());
        }
        // Add "main" unit page if needed
        if (!existsUnitPageMain()) {
            UnitPage main = new UnitPage("main", IconBuilder.create(FontAwesome.FA_HOME, 24.0).styleClass("icon-fill").build(), resources.getString("page.main"));
            main.setOrder(0);
            appunitpages.add(main);
        }
        mainnode.construct(appunitpages);

        // Construct All
        for (Unit s : appunits) {
            s.construct(this);
        }
        for (Device d : appdevices) {
            d.construct(topicsmanager);
        }

        connection();
    }

    private boolean existsUnitPageMain() {
        for (UnitPage p : appunitpages) {
            if ("main".equals(p.getName())) {
                return true;
            }
        }
        return false;
    }

    private void connection() {
        // connect !!!
        styleConnection.run();
    }

    private void oneConnection() {
        ultraConnection(3, Duration.ZERO);
    }

    private void tryConnection() {
        ultraConnection(Integer.MAX_VALUE, Duration.ZERO);
    }

    private void ultraConnection(int retries, Duration d) {
        mainnode.showConnecting();
        ultraConnectionImpl(retries, d);
    }

    private void ultraConnectionImpl(int retries, Duration d) {
        doTimeout(d, e -> {
            CompletableAsync.handle(topicsmanager.open(), 
                v -> {
                    mainnode.hideConnecting();
                    mainnode.start();
                },
                ex -> {
                    if (retries == 0) {
                        mainnode.hideConnecting();
                        showConnectionException(ex);
                    } else {
                        ultraConnectionImpl(retries - 1, Duration.seconds(2.5));
                    }
                });
        });
    }

    private void showConnectionException(Throwable t) {

        AtomicBoolean isok = new AtomicBoolean(false);

        DialogView dialog = new DialogView();
        dialog.setTitle(resources.getString("title.errorconnection"));
        DialogException contentex = new DialogException();
        contentex.setMessage(t.getLocalizedMessage());
        contentex.setException(t);
        dialog.setContent(contentex.getNode());
        dialog.setIndicator(IconBuilder.create(FontAwesome.FA_TIMES_CIRCLE, 48.0).apply(new FillPaint(Color.web("#FF9999"))).apply(new Shine(Color.RED)).build());
        dialog.setActionDispose((ActionEvent event) -> {
            if (isok.get()) {
                connection();
            } else {
                exitevent.handle(event);
            }
        });

        Button cancel = new Button(resources.getString("button.cancel"));
        cancel.setOnAction((ActionEvent event) -> {
            dialog.dispose();
        });
        ButtonBar.setButtonData(cancel, ButtonBar.ButtonData.CANCEL_CLOSE);

        Button retry = new Button(resources.getString("button.retry"));
        retry.setOnAction((ActionEvent event) -> {
            isok.set(true);
            dialog.dispose();
        });
        retry.setDefaultButton(true);
        ButtonBar.setButtonData(retry, ButtonBar.ButtonData.OK_DONE);

        dialog.addButtons(contentex.createButtonDetails(), cancel, retry);
        dialog.show(MessageUtils.getRoot(mainnode.getNode()));
    }

    public void stopAndDestroy() {
        mainnode.stop();
        CompletableAsync.handle(topicsmanager.close(),
             v -> {
                // Destroy all units
                for (Unit s : appunits) {
                    s.destroy();
                }
                for (Device d : appdevices) {
                    d.destroy();
                }

                mainnode.destroy();
            },
            ex -> {
                // Destroy all units
                for (Unit s : appunits) {
                    s.destroy();
                }
                for (Device d : appdevices) {
                    d.destroy();
                }

                mainnode.destroy();
            });
    }

    public MainNode getMainNode() {
        return mainnode;
    }

    public List<Unit> getUnits() {
        return appunits;
    }

    public List<Device> getDevices() {
        return appdevices;
    }

    @Override
    public Device getDevice(String id) {
        for (Device d : appdevices) {
            if (id.equals(d.getId())) {
                return d;
            }
        }
        return null;
    }
    
    @Override
    public ListDevice getAllDevices() {
        return new ListDevice(appdevices);
    }    

    private void doTimeout(Duration duration, EventHandler<ActionEvent> eventhandler) {
        if (duration.greaterThan(Duration.ZERO)) {
            new Timeline(new KeyFrame(duration, eventhandler)).play();
        } else {
            eventhandler.handle(new ActionEvent());
        }
    }
}
