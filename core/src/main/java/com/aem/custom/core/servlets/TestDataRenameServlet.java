package com.aem.custom.core.servlets;


import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;

@Component(service = Servlet.class,
        property = {
                    "sling.servlet.paths=/bin/rename-data",
                "sling.servlet.methods=GET",
                "sling.servlet.extensions=json"})

public class TestDataRenameServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRenameServlet.class);

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        File folder = new File("C:\\AEM\\test");
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            int i = 0;
            for (File currentFile: listOfFiles){
                if (currentFile.isFile()) {
                    i++;
                    String currentFileExtension = currentFile.getName().split("\\.")[1];
                    currentFile.renameTo(new File("C:\\AEM\\test\\Asset-" + i + "." + currentFileExtension));
                }
            }
        }

        response.getWriter().write("Files Renamed");
    }
}
