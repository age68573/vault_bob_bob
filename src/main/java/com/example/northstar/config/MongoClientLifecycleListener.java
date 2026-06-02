package com.example.northstar.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class MongoClientLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ApplicationConfig.initialize();
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        MongoClientProvider.close();
        ApplicationConfig.close();
    }
}
